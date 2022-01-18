// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.jpsBootstrap;

import com.intellij.util.ExceptionUtil;
import org.apache.commons.cli.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.jetbrains.jpsBootstrap.JpsBootstrapUtil.*;

@SuppressWarnings({"UseOfSystemOutOrSystemErr", "SameParameterValue"})
public class JpsBootstrapMain {

  private static final String COMMUNITY_HOME_ENV = "JPS_BOOTSTRAP_COMMUNITY_HOME";
  private static final String JPS_BOOTSTRAP_WORK_DIR_ENV = "JPS_BOOTSTRAP_WORK_DIR";
  private static final String JPS_BOOTSTRAP_VERBOSE = "JPS_BOOTSTRAP_VERBOSE";

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
      new JpsBootstrapMain(args).main();
      System.exit(0);
    }
    catch (Throwable t) {
      fatal(ExceptionUtil.getThrowableText(t));
      System.exit(1);
    }
  }

  private final Path projectHome;
  private final Path communityHome;
  private final String moduleNameToRun;
  private final String classNameToRun;
  private final Path jpsBootstrapWorkDir;
  private final Path ideaHomePath;
  private final String[] mainArgsToRun;

  public JpsBootstrapMain(String[] args) throws IOException {
    CommandLine cmdline;
    try {
      cmdline = (new DefaultParser()).parse(createCliOptions(), args, true);
    }
    catch (ParseException e) {
      e.printStackTrace();
      showUsagesAndExit();
      throw new IllegalStateException("NOT_REACHED");
    }

    final List<String> freeArgs = Arrays.asList(cmdline.getArgs());
    if (cmdline.hasOption(ARG_HELP) || freeArgs.size() < 2) {
      showUsagesAndExit();
    }

    moduleNameToRun = freeArgs.get(0);
    classNameToRun = freeArgs.get(1);

    if (!classNameToRun.endsWith("BuildTarget")) {
      fatal("Class name must end with 'BuildTarget': " + classNameToRun +
        "\nThis is just a convention helping to find build targets in the monorepo");
    }

    String verboseEnv = System.getenv(JPS_BOOTSTRAP_VERBOSE);
    JpsBootstrapUtil.setVerboseEnabled(cmdline.hasOption(ARG_VERBOSE) || (verboseEnv != null && toBooleanChecked(verboseEnv)));

    String communityHomeString = System.getenv(COMMUNITY_HOME_ENV);
    if (communityHomeString == null) fatal("Please set " + COMMUNITY_HOME_ENV + " environment variable");

    communityHome = Path.of(communityHomeString);

    Path communityCheckFile = communityHome.resolve("intellij.idea.community.main.iml");
    if (!Files.exists(communityCheckFile)) fatal(COMMUNITY_HOME_ENV + " is incorrect: " + communityCheckFile + " is missing");

    Path riderHome = communityHome.getParent().getParent().resolve("Frontend");
    Path riderCheckFile = riderHome.resolve("Rider.iml");

    Path ultimateHome = communityHome.getParent();
    Path ultimateCheckFile = ultimateHome.resolve("intellij.idea.ultimate.main.iml");

    if (Files.exists(riderCheckFile)) {
      projectHome = riderHome;
      ideaHomePath = ultimateHome;
    }
    else if (Files.exists(ultimateCheckFile)) {
      projectHome = ultimateHome;
      ideaHomePath = ultimateHome;
    }
    else {
      warn("Ultimate repository is not detected by checking '" + ultimateCheckFile + "', using only community project");
      projectHome = communityHome;
      ideaHomePath = communityHome;
    }

    if (System.getenv(JPS_BOOTSTRAP_WORK_DIR_ENV) != null) {
      jpsBootstrapWorkDir = Path.of(System.getenv(JPS_BOOTSTRAP_WORK_DIR_ENV));
    }
    else {
      jpsBootstrapWorkDir = communityHome.resolve("out").resolve("jps-bootstrap");
    }

    info("Working directory: " + jpsBootstrapWorkDir);
    Files.createDirectories(jpsBootstrapWorkDir);

    mainArgsToRun = freeArgs.subList(2, freeArgs.size()).toArray(new String[0]);
  }

  private void main() throws Throwable {
    Properties savedProperties = System.getProperties();

    JpsModel model = JpsProjectUtils.loadJpsProject(projectHome);
    JpsModule module = JpsProjectUtils.getModuleByName(model, moduleNameToRun);

    loadClasses(module, model);

    System.setProperties(savedProperties);
    setSystemPropertiesFromTeamCityBuild();
    runMainFromModuleRuntimeClasspath(classNameToRun, mainArgsToRun, module);
  }

  private void loadClasses(JpsModule module, JpsModel model) throws Throwable {
    String fromJpsBuildEnvValue = System.getenv(JpsBuild.CLASSES_FROM_JPS_BUILD_ENV_NAME);
    boolean runJpsBuild = fromJpsBuildEnvValue != null && JpsBootstrapUtil.toBooleanChecked(fromJpsBuildEnvValue);

    String manifestJsonUrl = System.getenv(ClassesFromCompileInc.MANIFEST_JSON_URL_ENV_NAME);

    if (runJpsBuild && manifestJsonUrl != null) {
      throw new IllegalStateException("Both env. variables are set, choose only one: " +
        JpsBuild.CLASSES_FROM_JPS_BUILD_ENV_NAME + " " +
        ClassesFromCompileInc.MANIFEST_JSON_URL_ENV_NAME);
    }

    if (!runJpsBuild && manifestJsonUrl == null) {
      // Nothing specified. It's ok locally, but on buildserver we must be sure
      if (underTeamCity) {
        throw new IllegalStateException("On buildserver one of the following env. variables must be set: " +
          JpsBuild.CLASSES_FROM_JPS_BUILD_ENV_NAME + " " +
          ClassesFromCompileInc.MANIFEST_JSON_URL_ENV_NAME);
      }
    }

    JpsBuild jpsBuild = new JpsBuild(ideaHomePath, model, jpsBootstrapWorkDir);
    if (manifestJsonUrl != null) {
      jpsBuild.resolveProjectDependencies();
      info("Downloading project classes from " + manifestJsonUrl);
      ClassesFromCompileInc.downloadProjectClasses(model.getProject(), communityHome);
    } else {
      jpsBuild.buildModule(module);
    }
  }

  private static String urlDebugInfo(URL url) {
    try {
      File file = new File(url.toURI());

      if (file.exists()) {
        if (file.isDirectory()) {
          return url + " directory";
        }
        else {
          long length = file.length();
          String sha256 = DigestUtils.sha256Hex(Files.readAllBytes(file.toPath()));
          return url + " file length " + length + " sha256 " + sha256;
        }
      }
      else {
        return url + " missing file";
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static void runMainFromModuleRuntimeClasspath(String className, String[] args, JpsModule module) throws Throwable {
    List<URL> moduleRuntimeClasspath = JpsProjectUtils.getModuleRuntimeClasspath(module);
    verbose("Module " + module.getName() + " classpath:\n  " + moduleRuntimeClasspath.stream().map(JpsBootstrapMain::urlDebugInfo).collect(Collectors.joining("\n  ")));

    info("Running class " + className + " from module " + module.getName());
    try (URLClassLoader classloader = new URLClassLoader(moduleRuntimeClasspath.toArray(new URL[0]), ClassLoader.getPlatformClassLoader())) {
      // Some clients peek into context class loaders
      // see org.apache.log4j.helpers.Loader#loadClass
      Thread.currentThread().setContextClassLoader(classloader);

      Class<?> mainClass;
      try {
        mainClass = classloader.loadClass(className);
      }
      catch (ClassNotFoundException ex) {
        final String message = "Class '" + className + "' was not found in runtime classpath of module " + module.getName();
        info(message + ":\n  " + moduleRuntimeClasspath.stream().map(URL::toString).collect(Collectors.joining("\n  ")));
        throw new IllegalStateException(message + ". See the class path above");
      }

      //noinspection ConfusingArgumentToVarargsMethod
      MethodHandles.lookup()
        .findStatic(mainClass, "main", MethodType.methodType(Void.TYPE, String[].class))
        .invokeExact(args);
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

  @Contract("->fail")
  private static void showUsagesAndExit() {
    HelpFormatter formatter = new HelpFormatter();
    formatter.setWidth(1000);
    formatter.printHelp("./jps-bootstrap.sh [jps-bootstrap options] MODULE_NAME CLASS_NAME [arguments_passed_to_CLASS_NAME's_main]", createCliOptions());
    System.exit(1);
  }
}
