// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.jpsBootstrap;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.URLUtil;
import org.apache.commons.cli.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.groovy.compiler.rt.GroovyRtConstants;
import org.jetbrains.jps.api.GlobalOptions;
import org.jetbrains.jps.build.Standalone;
import org.jetbrains.jps.incremental.groovy.JpsGroovycRunner;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsNamedElement;
import org.jetbrains.jps.model.java.JpsJavaDependenciesEnumerator;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService;
import org.jetbrains.jps.model.serialization.JpsPathVariablesConfiguration;
import org.jetbrains.jps.model.serialization.JpsProjectLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static org.jetbrains.jpsBootstrap.JpsBootstrapUtil.*;

@SuppressWarnings({"UseOfSystemOutOrSystemErr", "SameParameterValue"})
public class JpsBootstrapMain {

  private static final String COMMUNITY_HOME_ENV = "JPS_BOOTSTRAP_COMMUNITY_HOME";
  private static final String JPS_BOOTSTRAP_WORK_DIR_ENV = "JPS_BOOTSTRAP_WORK_DIR";

  private static final String ARG_HELP = "help";
  private static final String ARG_VERBOSE = "verbose";

  private static Options createCliOptions() {
    Options opts = new Options();

    opts.addOption(Option.builder("h").longOpt("help").argName(ARG_HELP).build());
    opts.addOption(Option.builder("v").longOpt("verbose").desc("Show more logging from jps-bootstrap and the building process").argName(ARG_VERBOSE).build());

    return opts;
  }

  public static void main(String[] args) {
    try {
      mainImpl(args);
      System.exit(0);
    }
    catch (Throwable t) {
      t.printStackTrace(System.err);
      fatal("Fatal error occurred, see exception above");
      System.exit(1);
    }
  }

  @SuppressWarnings("ConfusingArgumentToVarargsMethod")
  private static void mainImpl(String[] args) throws Throwable {
    CommandLine cmdline;
    try {
      cmdline = (new DefaultParser()).parse(createCliOptions(), args, true);
    }
    catch (ParseException e) {
      e.printStackTrace();
      showUsagesAndExit();
      throw new IllegalStateException("NOT_REACHED");
    }

    final String[] freeArgs = cmdline.getArgs();
    if (cmdline.hasOption(ARG_HELP) || freeArgs.length < 2) {
      showUsagesAndExit();
    }

    JpsBootstrapUtil.setVerboseEnabled(cmdline.hasOption(ARG_VERBOSE));

    long startTime = System.currentTimeMillis();

    String moduleName = freeArgs[0];
    String className = freeArgs[1];

    String communityHomeString = System.getenv(COMMUNITY_HOME_ENV);
    if (communityHomeString == null) fatal("Please set " + COMMUNITY_HOME_ENV + " environment variable");

    Path communityHome = Path.of(communityHomeString);

    Path communityCheckFile = communityHome.resolve("intellij.idea.community.main.iml");
    if (!Files.exists(communityCheckFile)) fatal(COMMUNITY_HOME_ENV + " is incorrect: " + communityCheckFile + " is missing");

    Path projectHome;

    Path ultimateCheckFile = communityHome.getParent().resolve("intellij.idea.ultimate.main.iml");
    if (Files.exists(ultimateCheckFile)) {
      projectHome = communityHome.getParent();
    }
    else {
      warn("Ultimate repository is not detected by checking '" + ultimateCheckFile + "', using only community project");
      projectHome = communityHome;
    }

    // Workaround for KTIJ-19065
    System.setProperty(PathManager.PROPERTY_HOME_PATH, projectHome.toString());

    Path workDir;

    if (System.getenv(JPS_BOOTSTRAP_WORK_DIR_ENV) != null) {
      workDir = Path.of(System.getenv(JPS_BOOTSTRAP_WORK_DIR_ENV));
    }
    else {
      workDir = communityHome.resolve("out").resolve("jps-bootstrap");
    }

    info("Working directory: " + workDir);

    Files.createDirectories(workDir);

    Path m2LocalRepository = Path.of(System.getProperty("user.home"), ".m2", "repository");
    JpsModel model = JpsElementFactory.getInstance().createModel();
    JpsPathVariablesConfiguration pathVariablesConfiguration =
      JpsModelSerializationDataService.getOrCreatePathVariablesConfiguration(model.getGlobal());
    pathVariablesConfiguration.addPathVariable(
      "MAVEN_REPOSITORY", FileUtilRt.toSystemIndependentName(m2LocalRepository.toAbsolutePath().toString()));

    System.setProperty("kotlin.incremental.compilation", "true");
    System.setProperty("kotlin.daemon.enabled", "false");
    System.setProperty(GlobalOptions.COMPILE_PARALLEL_OPTION, "true");

    Map<String, String> pathVariables = JpsModelSerializationDataService.computeAllPathVariables(model.getGlobal());
    JpsProjectLoader.loadProject(model.getProject(), pathVariables, projectHome.toString());
    System.out.println(
      "Loaded project " + projectHome + ": " +
        model.getProject().getModules().size() + " modules, " +
        model.getProject().getLibraryCollection().getLibraries().size() + " libraries in " +
        (System.currentTimeMillis() - startTime) + " ms");

    addSdk(model, "corretto-11", System.getProperty("java.home"));

    String url = "file://" + FileUtilRt.toSystemIndependentName(workDir.resolve("out").toString());
    JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(model.getProject()).setOutputUrl(url);

    System.setProperty(JpsGroovycRunner.GROOVYC_IN_PROCESS, "true");
    System.setProperty(GroovyRtConstants.GROOVYC_ASM_RESOLVING_ONLY, "false");
    System.setProperty(GlobalOptions.USE_DEFAULT_FILE_LOGGING_OPTION, "true");
    System.setProperty(GlobalOptions.LOG_DIR_OPTION, workDir.resolve("log").toString());
    System.out.println("Log: " + System.getProperty(GlobalOptions.LOG_DIR_OPTION));

    // kotlin.util.compiler-dependencies downloads all dependencies required for running Kotlin JPS compiler
    // see org.jetbrains.kotlin.idea.artifacts.KotlinArtifactsFromSources
    runBuild(model, workDir, "kotlin.util.compiler-dependencies");

    runBuild(model, workDir, moduleName);

    JpsModule module = model.getProject().getModules()
      .stream()
      .filter(m -> moduleName.equals(m.getName()))
      .findFirst().orElseThrow();
    JpsJavaDependenciesEnumerator enumerator = JpsJavaExtensionService
      .dependencies(module)
      .runtimeOnly()
      .productionOnly()
      .recursively()
      .withoutSdk();

    List<URL> roots = new ArrayList<>();
    for (File file : enumerator.classes().getRoots()) {
      URL toURL = file.toURI().toURL();
      roots.add(toURL);
    }
    roots.sort(Comparator.comparing(URL::toString));

    for (URL rootUrl : roots) {
      verbose("  CLASSPATH " + rootUrl);
    }

    setSystemPropertiesFromTeamCityBuild();

    info("Running class " + className + " from module " + moduleName);

    try (URLClassLoader classloader = new URLClassLoader(roots.toArray(new URL[0]), ClassLoader.getPlatformClassLoader())) {
      Class<?> mainClass;
      try {
        mainClass = classloader.loadClass(className);
      }
      catch (ClassNotFoundException ex) {
        for (URL rootUrl : roots) {
          info("  CLASSPATH " + rootUrl);
        }

        throw new IllegalStateException("Class '" + className + "' was not found. See the class path above");
      }

      MethodHandles.lookup()
        .findStatic(mainClass, "main", MethodType.methodType(Void.TYPE, String[].class))
        .invokeExact(Arrays.copyOfRange(freeArgs, 2, freeArgs.length));
    }
  }

  private static void setSystemPropertiesFromTeamCityBuild() throws IOException {
    if (!underTeamCity) return;

    final Properties systemProperties = getTeamCitySystemProperties();
    for (String propertyName : systemProperties.stringPropertyNames().stream().sorted().collect(Collectors.toList())) {
      String value = systemProperties.getProperty(propertyName);

      verbose("Setting system property '" + propertyName + "' to '" + value + "' from TeamCity build parameters");
      System.setProperty(propertyName, value);
    }
  }

  private static void runBuild(JpsModel model, Path workDir, String moduleName) throws Exception {
    final long buildStart = System.currentTimeMillis();
    final String[] firstError = {null};

    Path dataStorageRoot = workDir.resolve("jps-build-data");
    final Set<String> moduleNames = model.getProject().getModules().stream().map(JpsNamedElement::getName).collect(Collectors.toUnmodifiableSet());
    Standalone.runBuild(
      () -> model,
      dataStorageRoot.toFile(),
      false,
      ContainerUtil.set(moduleName),
      false,
      Collections.emptyList(),
      false,
      msg -> {
        BuildMessage.Kind kind = msg.getKind();
        String textAndKind = kind + " " + msg.getMessageText();

        switch (kind) {
          case PROGRESS:
            verbose(textAndKind);
            break;
          case WARNING:
            warn(textAndKind);
          case ERROR:
          case INTERNAL_BUILDER_ERROR:
            error(textAndKind);
            break;
          default:
            if (!msg.getMessageText().isBlank()) {
              if (moduleNames.contains(msg.getMessageText())) {
                verbose(textAndKind);
              }
              else {
                info(textAndKind);
              }
            }
            break;
        }

        if ((kind == BuildMessage.Kind.ERROR || kind == BuildMessage.Kind.INTERNAL_BUILDER_ERROR) && firstError[0] == null) {
          firstError[0] = msg.getMessageText();
        }
      }
    );

    System.out.println("Finished building '" + moduleName + "' in " + (System.currentTimeMillis() - buildStart) + " ms");

    if (firstError[0] != null) {
      fatal("Build finished with errors. First error: " + firstError[0]);
    }
  }

  private static List<String> readModulesFromReleaseFile(Path jdkDir) throws IOException {
    Path releaseFile = jdkDir.resolve("release");
    Properties p = new Properties();
    try (InputStream is = Files.newInputStream(releaseFile)) {
      p.load(is);
    }
    String jbrBaseUrl = URLUtil.JRT_PROTOCOL + URLUtil.SCHEME_SEPARATOR +
      FileUtil.toSystemIndependentName(jdkDir.toFile().getAbsolutePath()) +
      URLUtil.JAR_SEPARATOR;
    String modules = p.getProperty("MODULES");
    return ContainerUtil.map(StringUtil.split(StringUtil.unquoteString(modules), " "), s -> jbrBaseUrl + s);
  }

  private static void addSdk(JpsModel model, String sdkName, String sdkHome) throws IOException {
    JpsJavaExtensionService.getInstance().addJavaSdk(model.getGlobal(), sdkName, sdkHome);
    JpsLibrary additionalSdk = model.getGlobal().getLibraryCollection().findLibrary(sdkName);
    if (additionalSdk == null) {
      throw new IllegalStateException("SDK " + sdkHome + " was not found");
    }

    for (String moduleUrl : readModulesFromReleaseFile(Path.of(sdkHome))) {
      additionalSdk.addRoot(moduleUrl, JpsOrderRootType.COMPILED);
    }
  }

  @Contract("->fail")
  private static void showUsagesAndExit() {
    HelpFormatter formatter = new HelpFormatter();
    formatter.setWidth(1000);
    formatter.printHelp("./jps-bootstrap.sh [jps-bootstrap options] MODULE_NAME CLASS_NAME [arguments_passed_to_CLASS_NAME's_main]", createCliOptions());
    System.exit(1);
  }
}
