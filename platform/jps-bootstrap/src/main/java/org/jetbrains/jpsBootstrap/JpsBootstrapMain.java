// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.jpsBootstrap;

import com.intellij.execution.CommandLineWrapperUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ExceptionUtil;
import org.apache.commons.cli.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static org.jetbrains.jpsBootstrap.JpsBootstrapUtil.*;

@SuppressWarnings({"UseOfSystemOutOrSystemErr", "SameParameterValue"})
public class JpsBootstrapMain {

  private static final String COMMUNITY_HOME_ENV = "JPS_BOOTSTRAP_COMMUNITY_HOME";
  private static final String JPS_BOOTSTRAP_VERBOSE = "JPS_BOOTSTRAP_VERBOSE";

  private static final Option OPT_HELP = Option.builder("h").longOpt("help").build();
  private static final Option OPT_VERBOSE = Option.builder("v").longOpt("verbose").desc("Show more logging from jps-bootstrap and the building process").build();
  private static final Option OPT_SYSTEM_PROPERTY = Option.builder("D").hasArgs().valueSeparator('=').desc("Pass system property to the build script").build();
  private static final Option OPT_BUILD_TARGET_XMX = Option.builder().longOpt("build-target-xmx").hasArg().desc("Specify Xmx to run build script. default: 4g").build();
  private static final Option OPT_JAVA_ARGFILE_TARGET = Option.builder().longOpt("java-argfile-target").required().hasArg().desc("Write java argfile to this file").build();
  private static final List<Option> ALL_OPTIONS =
    Arrays.asList(OPT_HELP, OPT_VERBOSE, OPT_SYSTEM_PROPERTY, OPT_JAVA_ARGFILE_TARGET, OPT_BUILD_TARGET_XMX);

  private static Options createCliOptions() {
    Options opts = new Options();

    for (Option option : ALL_OPTIONS) {
      opts.addOption(option);
    }

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
  private final String buildTargetXmx;
  private final Path jpsBootstrapWorkDir;
  private final Path ideaHomePath;
  private final Path javaArgsFileTarget;
  private final List<String> mainArgsToRun;
  private final Properties additionalSystemProperties;

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
    if (cmdline.hasOption(OPT_HELP) || freeArgs.size() < 2) {
      showUsagesAndExit();
    }

    moduleNameToRun = freeArgs.get(0);
    classNameToRun = freeArgs.get(1);

    if (!classNameToRun.endsWith("BuildTarget")) {
      fatal("Class name must end with 'BuildTarget': " + classNameToRun +
        "\nThis is just a convention helping to find build targets in the monorepo");
    }

    additionalSystemProperties = cmdline.getOptionProperties("D");

    String verboseEnv = System.getenv(JPS_BOOTSTRAP_VERBOSE);
    JpsBootstrapUtil.setVerboseEnabled(cmdline.hasOption(OPT_VERBOSE) || (verboseEnv != null && toBooleanChecked(verboseEnv)));

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

    jpsBootstrapWorkDir = communityHome.resolve("out").resolve("jps-bootstrap");

    info("Working directory: " + jpsBootstrapWorkDir);
    Files.createDirectories(jpsBootstrapWorkDir);

    mainArgsToRun = freeArgs.subList(2, freeArgs.size());
    javaArgsFileTarget = Path.of(cmdline.getOptionValue(OPT_JAVA_ARGFILE_TARGET));
    buildTargetXmx = cmdline.hasOption(OPT_BUILD_TARGET_XMX) ? cmdline.getOptionValue(OPT_BUILD_TARGET_XMX) : "4g";
  }

  private void main() throws Throwable {
    JpsModel model = JpsProjectUtils.loadJpsProject(projectHome);
    JpsModule module = JpsProjectUtils.getModuleByName(model, moduleNameToRun);

    loadClasses(module, model);

    List<File> moduleRuntimeClasspath = JpsProjectUtils.getModuleRuntimeClasspath(module);
    verbose("Module " + module.getName() + " classpath:\n  " + moduleRuntimeClasspath.stream().map(JpsBootstrapMain::fileDebugInfo).collect(Collectors.joining("\n  ")));

    writeJavaArgfile(moduleRuntimeClasspath);
  }

  private void writeJavaArgfile(List<File> moduleRuntimeClasspath) throws IOException {
    List<String> args = new ArrayList<>();
    args.add("-ea");
    args.add("-Xmx" + buildTargetXmx);
    args.add("-Dfile.encoding=UTF-8"); // just in case
    args.add("-Djava.awt.headless=true");

    // This is required only for accommodating KotlinBinaries.ensureKotlinJpsPluginIsAddedToClassPath
    args.add("-Djava.system.class.loader=org.jetbrains.intellij.build.impl.BuildScriptsSystemClassLoader");

    args.addAll(systemPropertiesArgsFromTeamCityBuild());
    args.addAll(convertPropertiesToCommandLineArgs(additionalSystemProperties));

    args.add("-classpath");
    args.add(StringUtil.join(moduleRuntimeClasspath, File.pathSeparator));

    args.add("-Dbuild.script.launcher.main.class=" + classNameToRun);
    args.add("org.jetbrains.intellij.build.impl.BuildScriptLauncher");

    args.addAll(mainArgsToRun);

    CommandLineWrapperUtil.writeArgumentsFile(
      javaArgsFileTarget.toFile(),
      args,
      StandardCharsets.UTF_8
    );

    info("java argfile:\n" + Files.readString(javaArgsFileTarget));
  }

  private void loadClasses(JpsModule module, JpsModel model) throws Throwable {
    String fromJpsBuildEnvValue = System.getenv(JpsBuild.CLASSES_FROM_JPS_BUILD_ENV_NAME);
    boolean runJpsBuild = fromJpsBuildEnvValue != null && JpsBootstrapUtil.toBooleanChecked(fromJpsBuildEnvValue);

    String manifestJsonUrl = System.getenv(ClassesFromCompileInc.MANIFEST_JSON_URL_ENV_NAME);
    if (manifestJsonUrl != null && manifestJsonUrl.isBlank()) {
      manifestJsonUrl = null;
    }

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

    Set<JpsModule> modulesSubset = JpsProjectUtils.getRuntimeModulesClasspath(module);

    JpsBuild jpsBuild = new JpsBuild(ideaHomePath, model, jpsBootstrapWorkDir);
    if (manifestJsonUrl != null) {
      jpsBuild.resolveProjectDependencies();
      info("Downloading project classes from " + manifestJsonUrl);
      ClassesFromCompileInc.downloadProjectClasses(model.getProject(), communityHome, modulesSubset);
    } else {
      jpsBuild.buildModules(modulesSubset);
    }
  }

  private static String fileDebugInfo(File file) {
    try {
      if (file.exists()) {
        if (file.isDirectory()) {
          return file + " directory";
        }
        else {
          long length = file.length();
          String sha256 = DigestUtils.sha256Hex(Files.readAllBytes(file.toPath()));
          return file + " file length " + length + " sha256 " + sha256;
        }
      }
      else {
        return file + " missing file";
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static List<String> systemPropertiesArgsFromTeamCityBuild() throws IOException {
    if (!underTeamCity) return Collections.emptyList();
    return convertPropertiesToCommandLineArgs(getTeamCitySystemProperties());
  }

  private static List<String> convertPropertiesToCommandLineArgs(Properties properties) {
    List<String> result = new ArrayList<>();
    for (String propertyName : properties.stringPropertyNames().stream().sorted().collect(Collectors.toList())) {
      String value = properties.getProperty(propertyName);

      result.add("-D" + propertyName + "=" + value);
    }
    return result;
  }

  @Contract("->fail")
  private static void showUsagesAndExit() {
    HelpFormatter formatter = new HelpFormatter();
    formatter.setWidth(1000);
    formatter.printHelp("./jps-bootstrap.sh [jps-bootstrap options] MODULE_NAME CLASS_NAME [arguments_passed_to_CLASS_NAME's_main]", createCliOptions());
    System.exit(1);
  }
}
