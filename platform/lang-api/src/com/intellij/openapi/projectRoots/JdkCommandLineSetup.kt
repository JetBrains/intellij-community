// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots

import com.intellij.execution.CantRunException
import com.intellij.execution.CommandLineWrapperUtil
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.Platform
import com.intellij.execution.configurations.GeneralCommandLine.ParentEnvironmentType
import com.intellij.execution.configurations.ParametersList
import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.execution.target.java.JavaLanguageRuntimeConfiguration
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.execution.target.value.TargetValue
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.encoding.EncodingManager
import com.intellij.util.PathUtil
import com.intellij.util.PathsList
import com.intellij.util.SystemProperties
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.text.nullize
import org.jetbrains.annotations.NonNls
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.collectResults
import java.io.File
import java.io.IOException
import java.net.MalformedURLException
import java.nio.charset.Charset
import java.nio.charset.IllegalCharsetNameException
import java.nio.charset.StandardCharsets
import java.nio.charset.UnsupportedCharsetException
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException
import java.util.jar.Manifest
import kotlin.math.abs

internal class JdkCommandLineSetup(private val request: TargetEnvironmentRequest,
                                   private val target: TargetEnvironmentConfiguration?) {

  val commandLine = TargetedCommandLineBuilder(request)
  val platform = request.targetPlatform.platform

  private val languageRuntime: JavaLanguageRuntimeConfiguration? = target?.runtimes?.findByType(
    JavaLanguageRuntimeConfiguration::class.java)

  private val classPathVolume by lazy { request.createTempVolume() }
  private val agentVolume by lazy { request.createTempVolume() }

  /* make private */
  val commandLineContent by lazy {
    mutableMapOf<String, String>().also { commandLine.putUserData(JdkUtil.COMMAND_LINE_CONTENT, it) }
  }

  @Throws(CantRunException::class)
  fun setupCommandLine(javaParameters: SimpleJavaParameters) {
    setupWorkingDirectory(javaParameters)
    setupEnvironment(javaParameters)
    setupClasspathAndParameters(javaParameters)
  }

  @Throws(CantRunException::class)
  fun setupJavaExePath(javaParameters: SimpleJavaParameters) {
    if (request is LocalTargetEnvironmentRequest || target == null) {
      val jdk = javaParameters.jdk ?: throw CantRunException(ExecutionBundle.message("run.configuration.error.no.jdk.specified"))
      val type = jdk.sdkType
      if (type !is JavaSdkType) throw CantRunException(ExecutionBundle.message("run.configuration.error.no.jdk.specified"))
      val exePath = (type as JavaSdkType).getVMExecutablePath(jdk)
                    ?: throw CantRunException(ExecutionBundle.message("run.configuration.cannot.find.vm.executable"))
      commandLine.setExePath(exePath)
    }
    else {
      if (languageRuntime == null) {
        throw CantRunException("Cannot find Java configuration in " + target.displayName + " target")
      }

      val java = if (platform == Platform.WINDOWS) "java.exe" else "java"
      commandLine.setExePath(joinPath(arrayOf(languageRuntime.homePath, "bin", java)))
    }
  }

  private fun setupWorkingDirectory(javaParameters: SimpleJavaParameters) {
    val workingDirectory = javaParameters.workingDirectory
    if (workingDirectory != null) {
      val remoteAppFolder = languageRuntime?.applicationFolder?.nullize()
      val volume = request.createUploadRoot(remoteAppFolder, false)
      commandLine.setWorkingDirectory(volume.createUpload(workingDirectory))
    }
  }

  @Throws(CantRunException::class)
  private fun setupEnvironment(javaParameters: SimpleJavaParameters) {
    javaParameters.env.forEach { (key: String, value: String?) -> commandLine.addEnvironmentVariable(key, value) }

    if (request is LocalTargetEnvironmentRequest) {
      val type = if (javaParameters.isPassParentEnvs) ParentEnvironmentType.CONSOLE else ParentEnvironmentType.NONE
      request.setParentEnvironmentType(type)
    }
  }

  @Throws(CantRunException::class)
  private fun setupClasspathAndParameters(javaParameters: SimpleJavaParameters) {
    val vmParameters = javaParameters.vmParametersList
    var dynamicClasspath = javaParameters.isDynamicClasspath
    val dynamicVMOptions = dynamicClasspath && javaParameters.isDynamicVMOptions && JdkUtil.useDynamicVMOptions()
    var dynamicParameters = dynamicClasspath && javaParameters.isDynamicParameters && JdkUtil.useDynamicParameters()
    var dynamicMainClass = false

    // copies agent .jar files to the beginning of the classpath to load agent classes faster
    if (vmParameters.isUrlClassloader()) {
      if (request !is LocalTargetEnvironmentRequest) {
        throw CantRunException("Cannot run application with UrlClassPath on the remote target.")
      }

      for (parameter in vmParameters.parameters) {
        if (parameter.startsWith(JAVAAGENT)) {
          val jar = parameter.substring(JAVAAGENT.length + 1).substringBefore('=')
          javaParameters.classPath.addFirst(jar)
        }
      }
    }

    if (dynamicClasspath) {
      val cs = StandardCharsets.UTF_8 // todo detect JNU charset from VM options?
      if (javaParameters.isArgFile) {
        setArgFileParams(javaParameters, vmParameters, dynamicVMOptions, dynamicParameters, cs)
        dynamicMainClass = dynamicParameters
      }
      else if (!vmParameters.isExplicitClassPath() && javaParameters.jarPath == null && commandLineWrapperClass != null) {
        if (javaParameters.isUseClasspathJar) {
          setClasspathJarParams(javaParameters, vmParameters, commandLineWrapperClass!!,
                                dynamicVMOptions, dynamicParameters)
        }
        else if (javaParameters.isClasspathFile) {
          JdkUtil.setCommandLineWrapperParams(this, commandLine, request, classPathVolume,
                                              languageRuntime, javaParameters,
                                              vmParameters, commandLineWrapperClass, dynamicVMOptions, dynamicParameters, cs)
        }
      }
      else {
        dynamicParameters = false
        dynamicClasspath = dynamicParameters
      }
    }
    if (!dynamicClasspath) {
      appendParamsEncodingClasspath(javaParameters, vmParameters)
    }

    if (!dynamicMainClass) {
      for (parameter in getMainClassParams(javaParameters)) {
        commandLine.addParameter(parameter)
      }
    }
    if (!dynamicParameters) {
      for (parameter in javaParameters.programParametersList.list) {
        commandLine.addParameter(parameter!!)
      }
    }
  }

  @Throws(CantRunException::class)
  private fun setArgFileParams(javaParameters: SimpleJavaParameters, vmParameters: ParametersList,
                               dynamicVMOptions: Boolean, dynamicParameters: Boolean,
                               cs: Charset) {

    try {
      val argFile = ArgFile(dynamicVMOptions, dynamicParameters, cs, platform)
      commandLine.addFileToDeleteOnTermination(argFile.file)

      val classPath = javaParameters.classPath
      if (!classPath.isEmpty && !vmParameters.isExplicitClassPath()) {
        argFile.addPromisedParameter("-classpath", composeClassPathValues(javaParameters, classPath))
      }

      val modulePath = javaParameters.modulePath
      if (!modulePath.isEmpty && !vmParameters.isExplicitModulePath()) {
        argFile.addPromisedParameter("-p", composeClassPathValues(javaParameters, modulePath))
      }

      if (dynamicParameters) {
        for (nextMainClassParam in getMainClassParams(javaParameters)) {
          argFile.addPromisedParameter(nextMainClassParam)
        }
      }

      if (!dynamicVMOptions) { // dynamic options will be handled later by ArgFile
        appendVmParameters(vmParameters)
      }

      argFile.scheduleWriteFileWhenReady(javaParameters, vmParameters)

      appendEncoding(javaParameters, vmParameters)

      val argFileParameter = classPathVolume.createUpload(argFile.file.absolutePath)
      commandLine.addParameter(TargetValue.map(argFileParameter) { s -> "@$s" })

      rememberFileContentAfterUpload(argFile.file, argFileParameter)
    }
    catch (e: IOException) {
      JdkUtil.throwUnableToCreateTempFile(e)
    }
  }

  /*make private*/
  @Throws(CantRunException::class)
  fun setClasspathJarParams(javaParameters: SimpleJavaParameters, vmParameters: ParametersList,
                            commandLineWrapper: Class<*>,
                            dynamicVMOptions: Boolean,
                            dynamicParameters: Boolean) {

    try {
      val jarFile = ClasspathJar(this, vmParameters.hasParameter(JdkUtil.PROPERTY_DO_NOT_ESCAPE_CLASSPATH_URL))
      commandLine.addFileToDeleteOnTermination(jarFile.file)

      jarFile.addToManifest("Created-By", ApplicationNamesInfo.getInstance().fullProductName, true)
      if (dynamicVMOptions) {
        val properties: MutableList<String> = ArrayList()
        for (param in vmParameters.list) {
          if (JdkUtil.isUserDefinedProperty(param)) {
            properties.add(param)
          }
          else {
            appendVmParameter(param)
          }
        }
        jarFile.addToManifest("VM-Options", ParametersListUtil.join(properties))
      }
      else {
        appendVmParameters(vmParameters)
      }

      appendEncoding(javaParameters, vmParameters)

      if (dynamicParameters) {
        jarFile.addToManifest("Program-Parameters", ParametersListUtil.join(javaParameters.programParametersList.list))
      }


      val targetJarFile = classPathVolume.createUpload(jarFile.file.absolutePath)
      if (dynamicVMOptions || dynamicParameters) {
        // -classpath path1:path2 CommandLineWrapper path2
        commandLine.addParameter("-classpath")
        commandLine.addParameter(composePathsList(
          classPathVolume.createUpload(PathUtil.getJarPathForClass(commandLineWrapper)),
          targetJarFile
        ))
        commandLine.addParameter(TargetValue.fixed(commandLineWrapper.name))
        commandLine.addParameter(targetJarFile)
      }
      else {
        // -classpath path2
        commandLine.addParameter("-classpath")
        commandLine.addParameter(targetJarFile)
      }
      val classPathParameters = getClassPathValues(javaParameters, javaParameters.classPath)
      jarFile.scheduleWriteFileWhenClassPathReady(classPathParameters, targetJarFile)
    }
    catch (e: IOException) {
      JdkUtil.throwUnableToCreateTempFile(e)
    }

    appendModulePath(javaParameters, vmParameters)
  }

  @JvmName("getMainClassParams")
  @Throws(CantRunException::class)
  /*make private */ internal fun getMainClassParams(javaParameters: SimpleJavaParameters): List<TargetValue<String>> {
    val mainClass = javaParameters.mainClass
    val moduleName = javaParameters.moduleName
    val jarPath = javaParameters.jarPath

    return if (mainClass != null && moduleName != null) {
      listOf(TargetValue.fixed("-m"), TargetValue.fixed("$moduleName/$mainClass"))
    }
    else if (mainClass != null) {
      listOf(TargetValue.fixed(mainClass))
    }
    else if (jarPath != null) {
      listOf(TargetValue.fixed("-jar"), classPathVolume.createUpload(jarPath))
    }
    else {
      throw CantRunException(ExecutionBundle.message("main.class.is.not.specified.error.message"))
    }
  }

  // todo[remoteServers]: problem here (?), it modifies the command (via commandLineContent) but has to be called AFTER value is resolved
  /* make private*/
  @JvmName("rememberFileContentAfterUpload")
  internal fun rememberFileContentAfterUpload(localFile: File, fileUpload: TargetValue<String>) {
    fileUpload.targetValue.onSuccess { resolvedTargetPath: String ->
      try {
        commandLineContent[resolvedTargetPath] = FileUtil.loadFile(localFile)
      }
      catch (e: IOException) {
        LOG.error("Cannot add command line content for $resolvedTargetPath from $localFile", e)
      }
    }
  }

  @JvmName("appendVmParameters")
  /* make private*/ internal fun appendVmParameters(vmParameters: ParametersList) {
    vmParameters.list.forEach {
      appendVmParameter(it)
    }
  }

  @JvmName("appendVmParameter")
  /* make private*/ internal fun appendVmParameter(vmParameter: String) {
    if (request is LocalTargetEnvironmentRequest ||
        SystemProperties.getBooleanProperty("run.targets.ignore.vm.parameter", false)) {
      commandLine.addParameter(vmParameter)
      return
    }

    if (vmParameter.startsWith("-agentpath:")) {
      appendVmAgentParameter(vmParameter, "-agentpath:")
    }
    else if (vmParameter.startsWith("-javaagent:")) {
      appendVmAgentParameter(vmParameter, "-javaagent:")
    }
    else {
      commandLine.addParameter(vmParameter)
    }
  }

  private fun appendVmAgentParameter(vmParameter: String, prefix: String) {
    val value = StringUtil.trimStart(vmParameter, prefix)
    val equalsSign = value.indexOf('=')
    val path = if (equalsSign > -1) value.substring(0, equalsSign) else value
    if (!path.endsWith(".jar")) {
      // ignore non-cross-platform agents
      return
    }
    val suffix = if (equalsSign > -1) value.substring(equalsSign) else ""
    commandLine.addParameter(TargetValue.map(agentVolume.createUpload(path)) { v: String ->
      prefix + v + suffix
    })
  }

  @JvmName("appendEncoding")
  internal fun appendEncoding(javaParameters: SimpleJavaParameters, parametersList: ParametersList) {
    // for correct handling of process's input and output, values of file.encoding and charset of CommandLine object should be in sync
    val encoding = parametersList.getPropertyValue("file.encoding")
    if (encoding == null) {
      val charset = javaParameters.charset ?: EncodingManager.getInstance().defaultCharset
      commandLine.addParameter("-Dfile.encoding=" + charset.name())
      commandLine.setCharset(charset)
    }
    else {
      try {
        commandLine.setCharset(Charset.forName(encoding))
      }
      catch (ignore: UnsupportedCharsetException) {
      }
      catch (ignore: IllegalCharsetNameException) {
      }
    }
  }

  @JvmName("appendModulePath")
  internal fun appendModulePath(javaParameters: SimpleJavaParameters, vmParameters: ParametersList) {
    val modulePath = javaParameters.modulePath
    if (!modulePath.isEmpty && !vmParameters.isExplicitModulePath()) {
      commandLine.addParameter("-p")
      commandLine.addParameter(composeClassPathValues(javaParameters, modulePath))
    }
  }

  private fun appendParamsEncodingClasspath(javaParameters: SimpleJavaParameters, vmParameters: ParametersList) {
    appendVmParameters(vmParameters)
    appendEncoding(javaParameters, vmParameters)

    val classPath = javaParameters.classPath
    if (!classPath.isEmpty && !vmParameters.isExplicitClassPath()) {
      commandLine.addParameter("-classpath")
      commandLine.addParameter(composeClassPathValues(javaParameters, classPath))
    }

    appendModulePath(javaParameters, vmParameters)
  }

  @JvmName("composeClassPathValues")
  internal fun composeClassPathValues(javaParameters: SimpleJavaParameters, classPath: PathsList): TargetValue<String> {
    val pathValues = getClassPathValues(javaParameters, classPath)
    val separator = platform.pathSeparator.toString()
    return TargetValue.composite(pathValues) { values -> values.joinTo(StringBuilder(), separator).toString() }
  }

  @JvmName("getClassPathValues")
  internal fun getClassPathValues(javaParameters: SimpleJavaParameters, classPath: PathsList): List<TargetValue<String>> {
    val localJdkPath = javaParameters.jdk?.homePath
    val remoteJdkPath = languageRuntime?.homePath
    val result = ArrayList<TargetValue<String>>()

    for (path in classPath.pathList) {
      if (localJdkPath == null || remoteJdkPath == null || !path.startsWith(localJdkPath)) {
        result.add(classPathVolume.createUpload(path))
      }
      else {
        //todo[remoteServers]: revisit with "provided" volume (?)
        val separator = platform.fileSeparator
        result.add(TargetValue.fixed(FileUtil.toCanonicalPath(
          remoteJdkPath + separator + StringUtil.trimStart(path, localJdkPath), separator)))
      }
    }
    return result
  }

  fun composePathsList(vararg targetPaths: TargetValue<String>): TargetValue<String> {
    return TargetValue.composite(targetPaths.toList()) {
      it.joinTo(StringBuilder(), platform.pathSeparator.toString()).toString()
    }
  }

  private fun joinPath(segments: Array<String>) = segments.joinTo(StringBuilder(), platform.fileSeparator.toString()).toString()

  companion object {
    private const val JAVAAGENT = "-javaagent"
    private const val WRAPPER_CLASS = "com.intellij.rt.execution.CommandLineWrapper"

    private val LOG by lazy { Logger.getInstance(JdkCommandLineSetup::class.java) }

    private val commandLineWrapperClass by lazy {
      try {
        Class.forName(WRAPPER_CLASS)
      }
      catch (e: ClassNotFoundException) {
        null
      }
    }

    private fun ParametersList.isExplicitClassPath(): Boolean {
      return JdkUtil.explicitClassPath(this)
      //return this.hasParameter("-cp") || this.hasParameter("-classpath") || this.hasParameter("--class-path")
    }

    private fun ParametersList.isUrlClassloader(): Boolean {
      return JdkUtil.isUrlClassloader(this)
      //return UrlClassLoader::class.java.name == this.getPropertyValue("java.system.class.loader")
    }

    private fun ParametersList.isExplicitModulePath(): Boolean {
      return JdkUtil.explicitModulePath(this)
      //return this.hasParameter("-p") || this.hasParameter("--module-path")
    }
  }

  private class ArgFile @Throws(IOException::class) constructor(private val dynamicVMOptions: Boolean,
                                                                private val dynamicParameters: Boolean,
                                                                private val charset: Charset,
                                                                private val platform: Platform) {

    val file = FileUtil.createTempFile("idea_arg_file" + Random().nextInt(Int.MAX_VALUE), null)

    private val myPromisedOptionValues: MutableMap<String, TargetValue<String>> = LinkedHashMap()
    private val myPromisedParameters = mutableListOf<TargetValue<String>>()
    private val myAllPromises = mutableListOf<Promise<String>>()

    fun addPromisedParameter(@NonNls optionName: String, promisedValue: TargetValue<String>) {
      myPromisedOptionValues[optionName] = promisedValue
      registerPromise(promisedValue)
    }

    fun addPromisedParameter(promisedValue: TargetValue<String>) {
      myPromisedParameters.add(promisedValue)
      registerPromise(promisedValue)
    }

    fun scheduleWriteFileWhenReady(javaParameters: SimpleJavaParameters, vmParameters: ParametersList) {
      myAllPromises.collectResults().onSuccess {
        try {
          writeArgFileNow(javaParameters, vmParameters)
        }
        catch (e: IOException) {
          //todo[remoteServers]: interrupt preparing environment
        }
        catch (e: ExecutionException) {
          LOG.error("Couldn't resolve target value", e)
        }
        catch (e: TimeoutException) {
          LOG.error("Couldn't resolve target value", e)
        }
      }
    }

    @Throws(IOException::class, ExecutionException::class, TimeoutException::class)
    private fun writeArgFileNow(javaParameters: SimpleJavaParameters, vmParameters: ParametersList) {
      val fileArgs: MutableList<String?> = ArrayList()
      if (dynamicVMOptions) {
        fileArgs.addAll(vmParameters.list)
      }
      for ((nextOption, nextResolvedValue) in myPromisedOptionValues) {
        fileArgs.add(nextOption)
        fileArgs.add(nextResolvedValue.targetValue.blockingGet(0))
      }
      for (nextResolvedParameter in myPromisedParameters) {
        fileArgs.add(nextResolvedParameter.targetValue.blockingGet(0))
      }
      if (dynamicParameters) {
        fileArgs.addAll(javaParameters.programParametersList.list)
      }
      CommandLineWrapperUtil.writeArgumentsFile(file, fileArgs, platform.lineSeparator, charset)
    }

    private fun registerPromise(value: TargetValue<String>) {
      myAllPromises.add(value.targetValue)
    }
  }

  internal class ClasspathJar @Throws(IOException::class) constructor(private val setup: JdkCommandLineSetup,
                                                                      private val notEscapeClassPathUrl: Boolean) {

    private val manifest = Manifest()
    private val manifestText = StringBuilder()
    internal val file = FileUtil.createTempFile(
      CommandLineWrapperUtil.CLASSPATH_JAR_FILE_NAME_PREFIX + abs(Random().nextInt()), ".jar", true)

    fun addToManifest(key: String, value: String, skipInCommandLineContent: Boolean = false) {
      manifest.mainAttributes.putValue(key, value)
      if (!skipInCommandLineContent) {
        manifestText.append(key).append(": ").append(value).append("\n")
      }
    }

    fun scheduleWriteFileWhenClassPathReady(classpath: List<TargetValue<String>>, selfUpload: TargetValue<String>) {
      classpath.map { it.targetValue }.collectResults().onSuccess {
        try {
          writeFileNow(classpath, selfUpload)
        }
        catch (e: IOException) {
          //todo[remoteServers]: interrupt preparing environment
        }
        catch (e: ExecutionException) {
          LOG.error("Couldn't resolve target value", e)
        }
        catch (e: TimeoutException) {
          LOG.error("Couldn't resolve target value", e)
        }
      }
    }

    @Throws(ExecutionException::class, TimeoutException::class, IOException::class)
    private fun writeFileNow(resolvedTargetClasspath: List<TargetValue<String>>, selfUpload: TargetValue<String>) {

      val classPath = StringBuilder()
      for (parameter in resolvedTargetClasspath) {
        if (classPath.isNotEmpty()) classPath.append(' ')

        val localValue = parameter.localValue.blockingGet(0)
        val targetValue = parameter.targetValue.blockingGet(0)
        if (targetValue == null || localValue == null) {
          throw ExecutionException("Couldn't resolve target value", null)
        }

        val targetUrl = pathToUrl(targetValue)

        classPath.append(targetUrl)
        if (!StringUtil.endsWithChar(targetUrl, '/') && File(localValue).isDirectory) {
          classPath.append('/')
        }
      }

      // todo[remoteServers]: race condition here (?), it has to be called after classpath upload BUT before selfUpload
      CommandLineWrapperUtil.fillClasspathJarFile(manifest, classPath.toString(), file)

      selfUpload.targetValue.onSuccess { value: String ->
        val fullManifestText = manifestText.toString() + "Class-Path: " + classPath.toString()
        setup.commandLineContent[value] = fullManifestText
      }
    }

    @Throws(MalformedURLException::class)
    private fun pathToUrl(path: String): String {
      val file = File(path)
      @Suppress("DEPRECATION") val url = if (notEscapeClassPathUrl) file.toURL() else file.toURI().toURL()
      return url.toString()
    }
  }
}