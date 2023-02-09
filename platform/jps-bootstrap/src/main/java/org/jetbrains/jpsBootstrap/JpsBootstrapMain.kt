// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jpsBootstrap

import com.google.common.hash.Hashing
import com.intellij.execution.CommandLineWrapperUtil
import com.intellij.openapi.diagnostic.IdeaLogRecordFormatter
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.Strings
import com.intellij.util.ExceptionUtil
import jetbrains.buildServer.messages.serviceMessages.MessageWithAttributes
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageTypes
import org.apache.commons.cli.*
import org.jetbrains.annotations.Contract
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.BuildDependenciesLogging.fatal
import org.jetbrains.intellij.build.dependencies.BuildDependenciesLogging.info
import org.jetbrains.intellij.build.dependencies.BuildDependenciesLogging.setVerboseEnabled
import org.jetbrains.intellij.build.dependencies.BuildDependenciesLogging.verbose
import org.jetbrains.intellij.build.dependencies.JdkDownloader.getJavaExecutable
import org.jetbrains.intellij.build.dependencies.JdkDownloader.getJdkHome
import org.jetbrains.intellij.build.dependencies.TeamCityHelper.isUnderTeamCity
import org.jetbrains.jps.incremental.storage.ProjectStamps
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jpsBootstrap.JpsBootstrapUtil.toBooleanChecked
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import java.util.logging.ConsoleHandler
import java.util.logging.Level
import java.util.logging.Logger
import java.util.stream.Collectors
import kotlin.io.path.readLines
import kotlin.system.exitProcess

class JpsBootstrapMain(args: Array<String>?) {
  private val projectHome: Path
  private val communityHome: BuildDependenciesCommunityRoot
  private var moduleNameToRun: String? = null
  private var classNameToRun: String? = null
  private val buildTargetXmx: String
  private val jpsBootstrapWorkDir: Path
  private var javaArgsFileTarget: Path? = null
  private var mainArgsToRun: List<String>? = null
  private val additionalSystemProperties: Properties
  private val additionalSystemPropertiesFromPropertiesFile: Properties
  private val onlyDownloadJdk: Boolean

  init {
    initLogging()

    val cmdline = try {
      DefaultParser().parse(createCliOptions(), args, true)
    }
    catch (e: ParseException) {
      e.printStackTrace()
      showUsagesAndExit()
      throw IllegalStateException("NOT_REACHED")
    }

    val freeArgs = cmdline.args.toList()
    if (cmdline.hasOption(OPT_HELP) || freeArgs.isEmpty()) {
      showUsagesAndExit()
    }

    projectHome = Path.of(freeArgs.first()).normalize()
    onlyDownloadJdk = cmdline.hasOption(OPT_ONLY_DOWNLOAD_JDK)
    if (onlyDownloadJdk) {
      moduleNameToRun = null
      classNameToRun = null
      mainArgsToRun = emptyList()
      javaArgsFileTarget = null
    }
    else {
      moduleNameToRun = freeArgs[1]
      classNameToRun = freeArgs[2]
      mainArgsToRun = freeArgs.subList(3, freeArgs.size)
      javaArgsFileTarget = Path.of(cmdline.getOptionValue(OPT_JAVA_ARGFILE_TARGET))
    }
    additionalSystemProperties = cmdline.getOptionProperties("D")
    additionalSystemPropertiesFromPropertiesFile = Properties()
    if (cmdline.hasOption(OPT_PROPERTIES_FILE)) {
      val propertiesFile = Path.of(cmdline.getOptionValue(OPT_PROPERTIES_FILE))
      Files.newBufferedReader(propertiesFile).use { reader ->
        info("Loading properties from $propertiesFile")
        additionalSystemPropertiesFromPropertiesFile.load(reader)
      }
    }

    val verboseEnv = System.getenv(JPS_BOOTSTRAP_VERBOSE)
    setVerboseEnabled(cmdline.hasOption(OPT_VERBOSE) || (verboseEnv != null && verboseEnv.toBooleanChecked()))

    val communityHomeString = System.getenv(COMMUNITY_HOME_ENV)
      ?: error("Please set $COMMUNITY_HOME_ENV environment variable")
    communityHome = BuildDependenciesCommunityRoot(Path.of(communityHomeString))
    jpsBootstrapWorkDir = projectHome.resolve("build").resolve("jps-bootstrap-work")
    info("Working directory: $jpsBootstrapWorkDir")
    Files.createDirectories(jpsBootstrapWorkDir)
    buildTargetXmx = if (cmdline.hasOption(OPT_BUILD_TARGET_XMX)) cmdline.getOptionValue(OPT_BUILD_TARGET_XMX) else DEFAULT_BUILD_SCRIPT_XMX
  }

  private fun downloadJdk(): Path {
    val jdkHome: Path
    if (underTeamCity) {
      jdkHome = getJdkHome(communityHome)
      var setParameterServiceMessage = SetParameterServiceMessage(
        "jps.bootstrap.java.home", jdkHome.toString()
      )
      println(setParameterServiceMessage.asString())
      setParameterServiceMessage = SetParameterServiceMessage(
        "jps.bootstrap.java.executable", getJavaExecutable(jdkHome).toString())
      println(setParameterServiceMessage.asString())
    }
    else {
      // On local run JDK was already downloaded via jps-bootstrap.{sh,cmd}
      jdkHome = Path.of(System.getProperty("java.home"))
    }
    return jdkHome
  }

  @Throws(Throwable::class)
  private fun main() {
    val jdkHome = downloadJdk()
    if (onlyDownloadJdk) {
      return
    }

    /*
     * Enable dependencies resolution retries while building buildscript.
     * Don't override settings properties if they're already present in System.properties(), in additionalSystemProperties or
     * in additionalSystemPropertiesFromPropertiesFile.
     */
    val resolverRetrySettingsProperties = JpsBootstrapUtil.getJpsArtifactsResolutionRetryProperties(
      additionalSystemPropertiesFromPropertiesFile,
      additionalSystemProperties,
      System.getProperties()
    )
    resolverRetrySettingsProperties.forEach { k, v -> System.setProperty(k as String, v as String) }

    val kotlincHome = KotlinCompiler.downloadAndExtractKotlinCompiler(communityHome)
    val model = JpsProjectUtils.loadJpsProject(projectHome, jdkHome, kotlincHome)
    val module = JpsProjectUtils.getModuleByName(model, moduleNameToRun!!)
    downloadOrBuildClasses(module, model, kotlincHome)
    val moduleRuntimeClasspath = JpsProjectUtils.getModuleRuntimeClasspath(module)
    verbose("""Module ${module.name} classpath:
  ${moduleRuntimeClasspath.stream().map { file: File? -> fileDebugInfo(file) }.collect(Collectors.joining("\n  "))}""")
    writeJavaArgfile(moduleRuntimeClasspath)
  }

  private fun removeOpenedPackage(openedPackages: MutableList<String>, openedPackage: String, unknownPackages: MutableList<String>) {
    if (!openedPackages.remove(openedPackage)) {
      unknownPackages.add(openedPackage)
    }
  }

  @get:Throws(Exception::class)
  private val openedPackages: List<String>
    get() {
      val openedPackagesPath = communityHome.communityRoot.resolve("plugins/devkit/devkit-core/src/run/OpenedPackages.txt")
      val openedPackages = openedPackagesPath.readLines().filter { it.isNotBlank() }.toMutableList()
      val unknownPackages = mutableListOf<String>()
      if (!SystemInfo.isWindows) {
        removeOpenedPackage(openedPackages, "--add-opens=java.desktop/sun.awt.windows=ALL-UNNAMED", unknownPackages)
      }
      if (!SystemInfo.isMac) {
        removeOpenedPackage(openedPackages, "--add-opens=java.desktop/com.apple.eawt=ALL-UNNAMED", unknownPackages)
        removeOpenedPackage(openedPackages, "--add-opens=java.desktop/com.apple.eawt.event=ALL-UNNAMED", unknownPackages)
        removeOpenedPackage(openedPackages, "--add-opens=java.desktop/com.apple.laf=ALL-UNNAMED", unknownPackages)
        removeOpenedPackage(openedPackages, "--add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED", unknownPackages)
      }
      if (!SystemInfo.isLinux) {
        removeOpenedPackage(openedPackages, "--add-opens=java.desktop/com.sun.java.swing.plaf.gtk=ALL-UNNAMED", unknownPackages)
        removeOpenedPackage(openedPackages, "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED", unknownPackages)
        removeOpenedPackage(openedPackages, "--add-opens=java.desktop/sun.lwawt=ALL-UNNAMED", unknownPackages)
      }
      if (unknownPackages.isNotEmpty()) {
        throw Exception(String.format("OS specific opened packages: ['%s'] not found in '%s'. " +
          "Probably you need to clean up OS-specific package names in org.jetbrains.jpsBootstrap.JpsBootstrapMain",
          java.lang.String.join("','", unknownPackages), openedPackagesPath))
      }
      return openedPackages
    }

  @Throws(Exception::class)
  private fun writeJavaArgfile(moduleRuntimeClasspath: List<File?>?) {
    val systemProperties = Properties()
    if (underTeamCity) {
      systemProperties.putAll(JpsBootstrapUtil.teamCitySystemProperties)
    }
    systemProperties.putAll(additionalSystemPropertiesFromPropertiesFile)
    systemProperties.putAll(additionalSystemProperties)
    systemProperties.putIfAbsent("file.encoding", "UTF-8") // just in case
    systemProperties.putIfAbsent("java.awt.headless", "true")

    /*
 * Add dependencies resolution retries properties to argfile.
 * Don't override them if they're already present in additionalSystemProperties or in additionalSystemPropertiesFromPropertiesFile.
 */
    val resolverRetrySettingsProperties = JpsBootstrapUtil.getJpsArtifactsResolutionRetryProperties(
      additionalSystemPropertiesFromPropertiesFile,
      additionalSystemProperties
    )
    systemProperties.putAll(resolverRetrySettingsProperties)
    val args: MutableList<String> = ArrayList()
    args.add("-ea")
    args.add("-Xmx$buildTargetXmx")
    args.addAll(openedPackages)
    args.addAll(convertPropertiesToCommandLineArgs(systemProperties))
    args.add("-classpath")
    args.add(Strings.join(moduleRuntimeClasspath!!, File.pathSeparator))
    args.add("-Dbuild.script.launcher.main.class=$classNameToRun")
    args.add("org.jetbrains.intellij.build.impl.BuildScriptLauncher")
    args.addAll(mainArgsToRun!!)
    CommandLineWrapperUtil.writeArgumentsFile(
      javaArgsFileTarget!!.toFile(),
      args,
      StandardCharsets.UTF_8
    )
    info("""
    java argfile:
    ${Files.readString(javaArgsFileTarget)}
    """.trimIndent())
  }

  @Throws(Throwable::class)
  private fun downloadOrBuildClasses(module: JpsModule, model: JpsModel, kotlincHome: Path) {
    val fromJpsBuildEnvValue = System.getenv(JpsBuild.CLASSES_FROM_JPS_BUILD_ENV_NAME)
    val runJpsBuild = (fromJpsBuildEnvValue != null && fromJpsBuildEnvValue.toBooleanChecked()) || ProjectStamps.PORTABLE_CACHES

    var manifestJsonUrl = System.getenv(ClassesFromCompileInc.MANIFEST_JSON_URL_ENV_NAME)
    if (manifestJsonUrl != null && manifestJsonUrl.isBlank()) {
      manifestJsonUrl = null
    }

    check(!(runJpsBuild && manifestJsonUrl != null)) {
      "Both env. variables are set, choose only one: " +
        JpsBuild.CLASSES_FROM_JPS_BUILD_ENV_NAME + " " +
        ClassesFromCompileInc.MANIFEST_JSON_URL_ENV_NAME
    }
    if (!runJpsBuild && manifestJsonUrl == null) {
      // Nothing specified. It's ok locally, but on buildserver we must be sure
      check(!underTeamCity) {
        "On buildserver one of the following env. variables must be set: " +
          JpsBuild.CLASSES_FROM_JPS_BUILD_ENV_NAME + " " +
          ClassesFromCompileInc.MANIFEST_JSON_URL_ENV_NAME
      }
    }
    val modulesSubset = JpsProjectUtils.getRuntimeModulesClasspath(module)
    val jpsBuild = JpsBuild(communityHome, model, jpsBootstrapWorkDir, kotlincHome)

    // Some workarounds like 'kotlinx.kotlinx-serialization-compiler-plugin-for-compilation' library (used as Kotlin compiler plugin)
    // require that the corresponding library was downloaded. It's unclear from modules structure which libraries exactly required
    // so download them all
    //
    // In case of running from read-to-use classes we need all dependent libraries as well
    // Instead of calculating what libraries are exactly required, download them all
    jpsBuild.resolveProjectDependencies()
    if (manifestJsonUrl != null) {
      info("Downloading project classes from $manifestJsonUrl")
      ClassesFromCompileInc.downloadProjectClasses(model.project, communityHome, modulesSubset)
    }
    else {
      jpsBuild.buildModules(modulesSubset)
    }
  }

  private class SetParameterServiceMessage(name: String, value: String)
    : MessageWithAttributes(ServiceMessageTypes.BUILD_SET_PARAMETER, mapOf("name" to name, "value" to value))

  companion object {
    private const val DEFAULT_BUILD_SCRIPT_XMX = "4g"
    private const val COMMUNITY_HOME_ENV = "JPS_BOOTSTRAP_COMMUNITY_HOME"
    private const val JPS_BOOTSTRAP_VERBOSE = "JPS_BOOTSTRAP_VERBOSE"
    private val OPT_HELP = Option.builder("h").longOpt("help").build()
    private val OPT_VERBOSE = Option.builder("v").longOpt("verbose").desc("Show more logging from jps-bootstrap and the building process").build()
    private val OPT_SYSTEM_PROPERTY = Option.builder("D").hasArgs().valueSeparator('=').desc("Pass system property to the build script").build()
    private val OPT_PROPERTIES_FILE = Option.builder().longOpt("properties-file").hasArg().desc("Pass system properties to the build script from specified properties file https://en.wikipedia.org/wiki/.properties").build()
    private val OPT_BUILD_TARGET_XMX = Option.builder().longOpt("build-target-xmx").hasArg().desc("Specify Xmx to run build script. default: $DEFAULT_BUILD_SCRIPT_XMX").build()
    private val OPT_JAVA_ARGFILE_TARGET = Option.builder().longOpt("java-argfile-target").hasArg().desc("Write java argfile to this file").build()
    private val OPT_ONLY_DOWNLOAD_JDK = Option.builder().longOpt("download-jdk").desc("Download project JDK and exit").build()
    private val ALL_OPTIONS = listOf(OPT_HELP, OPT_VERBOSE, OPT_SYSTEM_PROPERTY, OPT_PROPERTIES_FILE, OPT_JAVA_ARGFILE_TARGET, OPT_BUILD_TARGET_XMX, OPT_ONLY_DOWNLOAD_JDK)
    val underTeamCity = isUnderTeamCity
    private fun createCliOptions(): Options {
      val opts = Options()
      for (option in ALL_OPTIONS) {
        opts.addOption(option)
      }
      return opts
    }

    @JvmStatic
    fun main(args: Array<String>) {
      var jpsBootstrapWorkDir: Path? = null
      try {
        val mainInstance = JpsBootstrapMain(args)
        @Suppress("UNUSED_VALUE")
        jpsBootstrapWorkDir = mainInstance.jpsBootstrapWorkDir
        mainInstance.main()
        exitProcess(0)
      }
      catch (t: Throwable) {
        fatal(ExceptionUtil.getThrowableText(t))

        // Better diagnostics for local users
        if (!isUnderTeamCity) {
          System.err.println("""

          ###### ERROR EXIT due to FATAL error: ${t.message}
     
          """.trimIndent())
          val work = jpsBootstrapWorkDir?.toString() ?: "PROJECT_HOME/build/jps-bootstrap-work"
          System.err.println("###### You may try to delete caches at $work and retry")
        }
        exitProcess(1)
      }
    }

    private fun fileDebugInfo(file: File?): String {
      return try {
        if (file!!.exists()) {
          val attributes = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
          if (attributes.isDirectory) {
            "$file directory"
          }
          else {
            val length = attributes.size()
            val sha256 = Hashing.sha256().hashBytes(Files.readAllBytes(file.toPath())).toString()
            "$file file length $length sha256 $sha256"
          }
        }
        else {
          "$file missing file"
        }
      }
      catch (e: Exception) {
        throw RuntimeException(e)
      }
    }

    private fun convertPropertiesToCommandLineArgs(properties: Properties): List<String> {
      return properties
        .map { (it.key as String) to (it.value as String) }
        .sortedBy { it.first }
        .map { "-D${it.first}=${it.second}" }
    }

    @Contract("->fail")
    private fun showUsagesAndExit() {
      val formatter = HelpFormatter()
      formatter.width = 1000
      formatter.printHelp("./jps-bootstrap.sh [jps-bootstrap options] MODULE_NAME CLASS_NAME [arguments_passed_to_CLASS_NAME's_main]", createCliOptions())
      exitProcess(1)
    }

    private fun initLogging() {
      val rootLogger = Logger.getLogger("")
      for (handler in rootLogger.handlers) {
        rootLogger.removeHandler(handler)
      }
      val layout = IdeaLogRecordFormatter()
      val consoleHandler = ConsoleHandler()
      consoleHandler.formatter = IdeaLogRecordFormatter(false, layout)
      consoleHandler.level = Level.WARNING
      rootLogger.addHandler(consoleHandler)
    }
  }
}
