// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots

import com.intellij.execution.CantRunException
import com.intellij.execution.CommandLineWrapperUtil
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.Platform
import com.intellij.execution.configurations.CompositeParameterTargetedValue
import com.intellij.execution.configurations.GeneralCommandLine.ParentEnvironmentType
import com.intellij.execution.configurations.ParameterTargetValuePart
import com.intellij.execution.configurations.ParametersList
import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.execution.target.LanguageRuntimeType.VolumeDescriptor
import com.intellij.execution.target.LanguageRuntimeType.VolumeType
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.execution.target.java.JavaLanguageRuntimeConfiguration
import com.intellij.execution.target.java.JavaLanguageRuntimeType
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.execution.target.value.DeferredTargetValue
import com.intellij.execution.target.value.TargetValue
import com.intellij.lang.LangCoreBundle
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.encoding.EncodingManager
import com.intellij.util.PathUtil
import com.intellij.util.PathsList
import com.intellij.util.SystemProperties
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.io.URLUtil
import com.intellij.util.io.isDirectory
import com.intellij.util.lang.UrlClassLoader
import org.jetbrains.annotations.NonNls
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.collectResults
import java.io.File
import java.io.IOException
import java.net.MalformedURLException
import java.net.URI
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.IllegalCharsetNameException
import java.nio.charset.StandardCharsets
import java.nio.charset.UnsupportedCharsetException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException
import java.util.jar.Manifest
import kotlin.math.abs

class JdkCommandLineSetup(private val request: TargetEnvironmentRequest) {

  init {
    request.onEnvironmentPrepared { environment, progressIndicator -> provideEnvironment(environment, progressIndicator) }
  }

  val commandLine = TargetedCommandLineBuilder(request)
  val platform = request.targetPlatform.platform

  private val languageRuntime: JavaLanguageRuntimeConfiguration? = request.configuration?.runtimes?.findByType(
    JavaLanguageRuntimeConfiguration::class.java)

  private val environmentPromise = AsyncPromise<Pair<TargetEnvironment, TargetProgressIndicator>>()
  private val dependingOnEnvironmentPromise = mutableListOf<Promise<Unit>>()
  private val uploads = mutableListOf<Upload>()

  private val projectHomeOnTarget = VolumeDescriptor(VolumeType(JdkCommandLineSetup::class.java.simpleName + ":projectHomeOnTarget"),
                                                     "", "", "",
                                                     request.projectPathOnTarget)

  /**
   * @param uploadPathIsFile
   *   * true: [uploadPathString] points to a file, the volume should be created for the file's directory.
   *   * false: [uploadPathString] points to a directory, the volume should be created for the path.
   *   * null: Determine whether [uploadPathString] is a file or a directory. If [uploadPathString] does not exist, it is treated as file.
   */
  private fun requestUploadIntoTarget(volumeDescriptor: VolumeDescriptor,
                                      uploadPathString: String,
                                      uploadPathIsFile: Boolean? = null,
                                      afterUploadResolved: (String) -> Unit = {}): TargetValue<String> {

    val uploadPath = Paths.get(FileUtil.toSystemDependentName(uploadPathString))
    val isDir = uploadPathIsFile?.not() ?: uploadPath.isDirectory()
    val localRootPath =
      if (isDir) uploadPath
      else (uploadPath.parent ?: Paths.get("."))  // Normally, paths should be absolute, but there are tests that check relative paths.

    val uploadRoot = createUploadRoot(volumeDescriptor, localRootPath)
    request.uploadVolumes += uploadRoot
    val result = DeferredTargetValue(uploadPathString)
    dependingOnEnvironmentPromise += environmentPromise.then { (environment, targetProgressIndicator) ->
      if (targetProgressIndicator.isCanceled || targetProgressIndicator.isStopped) {
        result.stopProceeding()
        return@then
      }
      val volume = environment.uploadVolumes.getValue(uploadRoot)
      try {
        val relativePath = if (isDir) "." else uploadPath.fileName.toString()
        val resolvedTargetPath = volume.resolveTargetPath(relativePath)
        uploads.add(Upload(volume, relativePath))
        result.resolve(resolvedTargetPath)
        afterUploadResolved(resolvedTargetPath)
      }
      catch (t: Throwable) {
        LOG.warn(t)
        targetProgressIndicator.stopWithErrorMessage(LangCoreBundle.message("progress.message.failed.to.resolve.0.1", volume.localRoot,
                                                                        t.localizedMessage))
        result.resolveFailure(t)
      }
    }
    return result
  }

  @Suppress("SameParameterValue")
  private fun requestDownloadFromTarget(downloadPathString: String,
                                        downloadPathIsFile: Boolean? = null,
                                        afterDownloadResolved: (String) -> Unit = {}): TargetValue<String> {
    val downloadPath = Paths.get(FileUtil.toSystemDependentName(downloadPathString))
    val isDir = downloadPathIsFile?.not() ?: downloadPath.isDirectory()
    val localRootPath =
      if (isDir) downloadPath
      else (downloadPath.parent ?: Paths.get("."))  // Normally, paths should be absolute, but there are tests that check relative paths.

    val downloadRoot = TargetEnvironment.DownloadRoot(localRootPath = localRootPath,
                                                      targetRootPath = TargetEnvironment.TargetPath.Temporary())
    request.downloadVolumes += downloadRoot
    val result = DeferredTargetValue(downloadPathString)
    dependingOnEnvironmentPromise += environmentPromise.then { (environment, targetProgressIndicator) ->
      if (targetProgressIndicator.isCanceled || targetProgressIndicator.isStopped) {
        result.stopProceeding()
        return@then
      }
      val volume = environment.downloadVolumes.getValue(downloadRoot)
      try {
        val relativePath = if (isDir) "." else downloadPath.fileName.toString()
        val resolvedTargetPath = volume.resolveTargetPath(relativePath)
        result.resolve(resolvedTargetPath)
        afterDownloadResolved(resolvedTargetPath)
      }
      catch (t: Throwable) {
        LOG.warn(t)
        targetProgressIndicator.stopWithErrorMessage(LangCoreBundle.message("progress.message.failed.to.resolve.0.1", volume.localRoot,
                                                                        t.localizedMessage))
        result.resolveFailure(t)
      }
    }
    return result
  }

  private class Upload(val volume: TargetEnvironment.UploadableVolume, val relativePath: String)

  private fun createUploadRoot(volumeDescriptor: VolumeDescriptor, localRootPath: Path): TargetEnvironment.UploadRoot {
    return languageRuntime?.createUploadRoot(volumeDescriptor, localRootPath)
           ?: TargetEnvironment.UploadRoot(localRootPath = localRootPath,
                                           targetRootPath = TargetEnvironment.TargetPath.Temporary())
  }

  private val commandLineContent by lazy {
    mutableMapOf<String, String>().also { commandLine.putUserData(JdkUtil.COMMAND_LINE_CONTENT, it) }
  }

  private fun provideEnvironment(environment: TargetEnvironment,
                                 targetProgressIndicator: TargetProgressIndicator) {
    environmentPromise.setResult(environment to targetProgressIndicator)
    if (environment is TargetEnvironment.BatchUploader) {
      environment.runBatchUpload(uploads = ContainerUtil.map(uploads) {
        Pair(it.volume, it.relativePath)
      }, targetProgressIndicator = targetProgressIndicator)
    }
    else {
      for (upload in uploads) {
        upload.volume.upload(upload.relativePath, targetProgressIndicator)
      }
    }
    for (promise in dependingOnEnvironmentPromise) {
      promise.blockingGet(0)  // Just rethrows errors.
    }
  }

  @Throws(CantRunException::class)
  fun setupCommandLine(javaParameters: SimpleJavaParameters) {
    setupWorkingDirectory(javaParameters)
    setupEnvironment(javaParameters)
    setupClasspathAndParameters(javaParameters)
    commandLine.setRedirectErrorStreamFromRegistry()
  }

  @Throws(CantRunException::class)
  fun setupJavaExePath(javaParameters: SimpleJavaParameters) {
    if (request is LocalTargetEnvironmentRequest || request.configuration == null) {
      val jdk = javaParameters.jdk ?: throw CantRunException(ExecutionBundle.message("run.configuration.error.no.jdk.specified"))
      val type = jdk.sdkType
      if (type !is JavaSdkType) throw CantRunException(ExecutionBundle.message("run.configuration.error.no.jdk.specified"))
      val exePath = (type as JavaSdkType).getVMExecutablePath(jdk)
                    ?: throw CantRunException(ExecutionBundle.message("run.configuration.cannot.find.vm.executable"))
      commandLine.setExePath(exePath)
    }
    else {
      if (languageRuntime == null) {
        throw CantRunException(LangCoreBundle.message("error.message.cannot.find.java.configuration.in.0.target", request.configuration?.displayName))
      }

      val java = if (platform == Platform.WINDOWS) "java.exe" else "java"
      commandLine.setExePath(joinPath(arrayOf(languageRuntime.homePath, "bin", java)))
    }
  }

  private fun setupWorkingDirectory(javaParameters: SimpleJavaParameters) {
    val workingDirectory = javaParameters.workingDirectory
    if (workingDirectory != null) {
      val targetWorkingDirectory = requestUploadIntoTarget(projectHomeOnTarget, workingDirectory, uploadPathIsFile = false)
      commandLine.setWorkingDirectory(targetWorkingDirectory)
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
    if (isUrlClassloader(vmParameters)) {
      if (request !is LocalTargetEnvironmentRequest) {
        throw CantRunException(LangCoreBundle.message("error.message.cannot.run.application.with.urlclasspath.on.the.remote.target"))
      }

      for (parameter in vmParameters.parameters) {
        if (parameter.startsWith(JAVAAGENT)) {
          val jar = parameter.substring(JAVAAGENT.length + 1).substringBefore('=')
          javaParameters.classPath.addFirst(jar)
        }
      }
    }

    val commandLineWrapperClass = commandLineWrapperClass()

    if (dynamicClasspath) {
      val cs = StandardCharsets.UTF_8 // todo detect JNU charset from VM options?
      if (javaParameters.isArgFile) {
        setArgFileParams(javaParameters, vmParameters, dynamicVMOptions, dynamicParameters, cs)
        dynamicMainClass = dynamicParameters
      }
      else if (!vmParameters.isExplicitClassPath() && javaParameters.jarPath == null && commandLineWrapperClass != null) {
        if (javaParameters.isUseClasspathJar) {
          setClasspathJarParams(javaParameters, vmParameters, commandLineWrapperClass,
                                dynamicVMOptions, dynamicParameters)
        }
        else if (javaParameters.isClasspathFile) {
          setCommandLineWrapperParams(javaParameters, vmParameters, commandLineWrapperClass, dynamicVMOptions, dynamicParameters, cs)
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
      for (value in mapTargetValues(javaParameters.programParametersList.targetedList)) {
        commandLine.addParameter(value)
      }
    }
  }

  private fun mapTargetValues(parameterValues: Collection<CompositeParameterTargetedValue>): List<TargetValue<String>> {
    return parameterValues.map { parameter ->
      val values = mutableListOf<TargetValue<String>>()
      for (part in parameter.parts) {
        when (part) {
          is ParameterTargetValuePart.Const ->
            TargetValue.fixed(part.localValue)
          is ParameterTargetValuePart.Path ->
            requestUploadIntoTarget(JavaLanguageRuntimeType.CLASS_PATH_VOLUME, part.pathToUpload, null)
          is ParameterTargetValuePart.PathSeparator ->
            TargetValue.fixed(platform.pathSeparator.toString())
          is ParameterTargetValuePart.PromiseValue ->
            TargetValue.create(part.localValue, part.targetValue)
          else ->
            throw IllegalStateException("Unexpected parameter list part " + part.javaClass)
        }.let { values.add(it) }
      }
      TargetValue.composite(values) { it.joinToString(separator = "") }
    }
  }

  @Throws(CantRunException::class)
  private fun setArgFileParams(javaParameters: SimpleJavaParameters, vmParameters: ParametersList,
                               dynamicVMOptions: Boolean, dynamicParameters: Boolean,
                               cs: Charset) {

    try {
      val argFile = ArgFile(dynamicVMOptions, cs, platform)
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
        appendVmParameters(javaParameters, vmParameters)
      }

      appendEncoding(javaParameters, vmParameters)

      if (dynamicParameters) {
        val targetValues = mapTargetValues(javaParameters.programParametersList.targetedList)
        for (targetValue in targetValues) {
          argFile.addPromisedParameter(targetValue)
        }
      }

      val argFileParameter = requestUploadIntoTarget(JavaLanguageRuntimeType.CLASS_PATH_VOLUME, argFile.file.absolutePath, uploadPathIsFile = true)
      commandLine.addParameter(TargetValue.map(argFileParameter) { s -> "@$s" })

      argFile.scheduleWriteFileWhenReady(vmParameters) {
        rememberFileContentAfterUpload(argFile.file, argFileParameter)
      }

    }
    catch (e: IOException) {
      throwUnableToCreateTempFile(e)
    }
  }

  @Throws(CantRunException::class)
  private fun setClasspathJarParams(javaParameters: SimpleJavaParameters, vmParameters: ParametersList,
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
          if (isUserDefinedProperty(param)) {
            properties.add(param)
          }
          else {
            appendVmParameter(param)
          }
        }
        jarFile.addToManifest("VM-Options", ParametersListUtil.join(properties))
      }
      else {
        appendVmParameters(javaParameters, vmParameters)
      }

      appendEncoding(javaParameters, vmParameters)

      if (dynamicParameters) {
        jarFile.addToManifest("Program-Parameters", ParametersListUtil.join(javaParameters.programParametersList.list))
      }


      val targetJarFile = requestUploadIntoTarget(JavaLanguageRuntimeType.CLASS_PATH_VOLUME, jarFile.file.absolutePath, uploadPathIsFile = true)
      if (dynamicVMOptions || dynamicParameters) {
        // -classpath path1:path2 CommandLineWrapper path2
        commandLine.addParameter("-classpath")
        commandLine.addParameter(composePathsList(listOf(
          requestUploadIntoTarget(JavaLanguageRuntimeType.CLASS_PATH_VOLUME, PathUtil.getJarPathForClass(commandLineWrapper)),
          targetJarFile
        )))
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
      throwUnableToCreateTempFile(e)
    }

    appendModulePath(javaParameters, vmParameters)
  }

  @Throws(CantRunException::class)
  private fun setCommandLineWrapperParams(javaParameters: SimpleJavaParameters, vmParameters: ParametersList,
                                          commandLineWrapper: Class<*>,
                                          dynamicVMOptions: Boolean,
                                          dynamicParameters: Boolean,
                                          cs: Charset) {
    try {
      val pseudoUniquePrefix = Random().nextInt(Int.MAX_VALUE)

      var vmParamsFile: File? = null
      if (dynamicVMOptions) {
        val toWrite: MutableList<String> = ArrayList()
        for (param in vmParameters.list) {
          if (isUserDefinedProperty(param)) {
            toWrite.add(param)
          }
          else {
            appendVmParameter(param)
          }
        }
        if (toWrite.isNotEmpty()) {
          vmParamsFile = FileUtil.createTempFile("idea_vm_params$pseudoUniquePrefix", null)
          commandLine.addFileToDeleteOnTermination(vmParamsFile)
          CommandLineWrapperUtil.writeWrapperFile(vmParamsFile, toWrite, platform.lineSeparator, cs)
        }
      }
      else {
        appendVmParameters(javaParameters, vmParameters)
      }

      appendEncoding(javaParameters, vmParameters)

      var appParamsFile: File? = null
      if (dynamicParameters) {
        appParamsFile = FileUtil.createTempFile("idea_app_params$pseudoUniquePrefix", null)
        commandLine.addFileToDeleteOnTermination(appParamsFile)
        CommandLineWrapperUtil.writeWrapperFile(appParamsFile, javaParameters.programParametersList.list, platform.lineSeparator, cs)
      }

      val classpathFile = FileUtil.createTempFile("idea_classpath$pseudoUniquePrefix", null)
      commandLine.addFileToDeleteOnTermination(classpathFile)
      val classPathParameters = getClassPathValues(javaParameters, javaParameters.classPath)

      classPathParameters.map { it.targetValue }.collectResults().onSuccess { pathList ->
        CommandLineWrapperUtil.writeWrapperFile(classpathFile, pathList, platform.lineSeparator, cs)
      }

      val classpath: MutableSet<TargetValue<String>> = LinkedHashSet()
      classpath.add(requestUploadIntoTarget(JavaLanguageRuntimeType.CLASS_PATH_VOLUME, PathUtil.getJarPathForClass(commandLineWrapper)))
      // If kotlin agent starts it needs kotlin-stdlib in the classpath.
      javaParameters.classPath.rootDirs.forEach { rootDir ->
        rootDir.getUserData(JdkUtil.AGENT_RUNTIME_CLASSPATH)?.let {
          classpath.add(requestUploadIntoTarget(JavaLanguageRuntimeType.CLASS_PATH_VOLUME, it))
        }
      }
      if (isUrlClassloader(vmParameters)) {
        if (request !is LocalTargetEnvironmentRequest) {
          throw CantRunException(LangCoreBundle.message("error.message.cannot.run.application.with.urlclasspath.on.the.remote.target"))
        }

        // since request is known to be local we will simplify to TargetValue.fixed below
        classpath.add(TargetValue.fixed(PathUtil.getJarPathForClass(UrlClassLoader::class.java)))
        classpath.add(TargetValue.fixed(PathUtil.getJarPathForClass(StringUtilRt::class.java)))
        classpath.add(TargetValue.fixed(PathUtil.getJarPathForClass(Class.forName("gnu.trove.THashMap"))))

        //explicitly enumerate jdk classes as UrlClassLoader doesn't delegate to parent classloader when loading resources
        //which leads to exceptions when coverage instrumentation tries to instrument loader class and its dependencies
        javaParameters.jdk?.rootProvider?.getFiles(OrderRootType.CLASSES)?.forEach {
          val path = PathUtil.getLocalPath(it)
          if (StringUtil.isNotEmpty(path)) {
            classpath.add(TargetValue.fixed(path))
          }
        }
      }

      commandLine.addParameter("-classpath")
      commandLine.addParameter(composePathsList(classpath))

      commandLine.addParameter(commandLineWrapper.name)
      val classPathParameter = requestUploadIntoTarget(JavaLanguageRuntimeType.CLASS_PATH_VOLUME, classpathFile.absolutePath, uploadPathIsFile = true)
      commandLine.addParameter(classPathParameter)
      rememberFileContentAfterUpload(classpathFile, classPathParameter)

      if (vmParamsFile != null) {
        commandLine.addParameter("@vm_params")
        val vmParamsParameter = requestUploadIntoTarget(JavaLanguageRuntimeType.CLASS_PATH_VOLUME, vmParamsFile.absolutePath, uploadPathIsFile = true)
        commandLine.addParameter(vmParamsParameter)
        rememberFileContentAfterUpload(vmParamsFile, vmParamsParameter)
      }
      if (appParamsFile != null) {
        commandLine.addParameter("@app_params")
        val appParamsParameter = requestUploadIntoTarget(JavaLanguageRuntimeType.CLASS_PATH_VOLUME, appParamsFile.absolutePath, uploadPathIsFile = true)
        commandLine.addParameter(appParamsParameter)
        rememberFileContentAfterUpload(appParamsFile, appParamsParameter)
      }
    }
    catch (e: IOException) {
      throwUnableToCreateTempFile(e)
    }
  }

  @Throws(CantRunException::class)
  private fun getMainClassParams(javaParameters: SimpleJavaParameters): List<TargetValue<String>> {
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
      listOf(TargetValue.fixed("-jar"), requestUploadIntoTarget(projectHomeOnTarget, jarPath, uploadPathIsFile = true))
    }
    else {
      throw CantRunException(ExecutionBundle.message("main.class.is.not.specified.error.message"))
    }
  }

  private fun rememberFileContentAfterUpload(localFile: File, fileUpload: TargetValue<String>) {
    fileUpload.targetValue.onSuccess { resolvedTargetPath: String ->
      try {
        commandLineContent[resolvedTargetPath] = FileUtil.loadFile(localFile)
      }
      catch (e: IOException) {
        LOG.error("Cannot add command line content for $resolvedTargetPath from $localFile", e)
      }
    }
  }

  private fun appendVmParameters(javaParameters: SimpleJavaParameters, vmParameters: ParametersList) {
    vmParameters.list.forEach {
      appendVmParameter(it)
    }
    val targetDependentParameters = javaParameters.targetDependentParameters
    targetDependentParameters.asTargetParameters().forEach { javaParameterFunction ->
      val value = javaParameterFunction.apply(request)
      value.resolvePaths(
        uploadPathsResolver = { path ->
          path.beforeUploadOrDownloadResolved(path.localPath)
          requestUploadIntoTarget(JavaLanguageRuntimeType.AGENTS_VOLUME, path.localPath, uploadPathIsFile = true) { path.afterUploadOrDownloadResolved(it) }
        },
        downloadPathsResolver = { path ->
          path.beforeUploadOrDownloadResolved(path.localPath)
          requestDownloadFromTarget(path.localPath, true) { path.afterUploadOrDownloadResolved(it) }
        }
      )
      commandLine.addParameter(value.parameter)
    }
    dependingOnEnvironmentPromise += environmentPromise.then { (environment, _) ->
      targetDependentParameters.setTargetEnvironment(environment)
    }
  }

  private fun appendVmParameter(vmParameter: String) {
    if (request is LocalTargetEnvironmentRequest ||
        SystemProperties.getBooleanProperty("run.targets.ignore.vm.parameter", false)) {
      commandLine.addParameter(vmParameter)
      return
    }

    when {
      vmParameter.startsWith("-agentpath:") -> {
        appendVmAgentParameter(vmParameter, "-agentpath:")
      }
      vmParameter.startsWith("-javaagent:") -> {
        appendVmAgentParameter(vmParameter, "-javaagent:")
      }
      else -> {
        commandLine.addParameter(vmParameter)
      }
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
    commandLine.addParameter(
      TargetValue.map(requestUploadIntoTarget(JavaLanguageRuntimeType.AGENTS_VOLUME, path, uploadPathIsFile = true)) { v: String ->
        prefix + v + suffix
      })
  }

  private fun appendEncoding(javaParameters: SimpleJavaParameters, parametersList: ParametersList) {
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

  private fun appendModulePath(javaParameters: SimpleJavaParameters, vmParameters: ParametersList) {
    val modulePath = javaParameters.modulePath
    if (!modulePath.isEmpty && !vmParameters.isExplicitModulePath()) {
      commandLine.addParameter("-p")
      commandLine.addParameter(composeClassPathValues(javaParameters, modulePath))
    }
  }

  private fun appendParamsEncodingClasspath(javaParameters: SimpleJavaParameters, vmParameters: ParametersList) {
    appendVmParameters(javaParameters, vmParameters)
    appendEncoding(javaParameters, vmParameters)

    val classPath = javaParameters.classPath
    if (!classPath.isEmpty && !vmParameters.isExplicitClassPath()) {
      commandLine.addParameter("-classpath")
      commandLine.addParameter(composeClassPathValues(javaParameters, classPath))
    }

    appendModulePath(javaParameters, vmParameters)
  }

  private fun composeClassPathValues(javaParameters: SimpleJavaParameters, classPath: PathsList): TargetValue<String> {
    val pathValues = getClassPathValues(javaParameters, classPath)
    val separator = platform.pathSeparator.toString()
    return TargetValue.composite(pathValues) { values -> values.joinTo(StringBuilder(), separator).toString() }
  }

  private fun getClassPathValues(javaParameters: SimpleJavaParameters, classPath: PathsList): List<TargetValue<String>> {
    val localJdkPath = javaParameters.jdk?.homePath
    val remoteJdkPath = languageRuntime?.homePath
    val result = ArrayList<TargetValue<String>>()

    for (path in classPath.pathList) {
      if (localJdkPath == null || remoteJdkPath == null || !path.startsWith(localJdkPath)) {
        result.add(requestUploadIntoTarget(JavaLanguageRuntimeType.CLASS_PATH_VOLUME, path))
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

  private fun composePathsList(targetPaths: Collection<TargetValue<String>>): TargetValue<String> {
    return TargetValue.composite(targetPaths) {
      it.joinTo(StringBuilder(), platform.pathSeparator.toString()).toString()
    }
  }

  private fun joinPath(segments: Array<String>) = segments.joinTo(StringBuilder(), platform.fileSeparator.toString()).toString()

  companion object {
    private const val JAVAAGENT = "-javaagent"

    private val LOG by lazy { Logger.getInstance(JdkCommandLineSetup::class.java) }

    private fun commandLineWrapperClass(): Class<*>? {
      try {
        return Class.forName("com.intellij.rt.execution.CommandLineWrapper")
      }
      catch (e: ClassNotFoundException) {
        return null
      }
    }

    private fun ParametersList.isExplicitClassPath(): Boolean {
      return this.hasParameter("-cp") || this.hasParameter("-classpath") || this.hasParameter("--class-path")
    }

    private fun isUrlClassloader(parametersList: ParametersList): Boolean {
      return (parametersList.getPropertyValue("java.system.class.loader") ?: "").startsWith("com.intellij.util.lang.")
    }

    private fun ParametersList.isExplicitModulePath(): Boolean {
      return this.hasParameter("-p") || this.hasParameter("--module-path")
    }

    private fun isUserDefinedProperty(param: String): Boolean {
      return param.startsWith("-D") && !(param.startsWith("-Dsun.") || param.startsWith("-Djava."))
    }

    @Throws(CantRunException::class)
    private fun throwUnableToCreateTempFile(cause: IOException?) {
      throw CantRunException(LangCoreBundle.message("error.message.failed.to.create.a.temporary.file.in.0", FileUtil.getTempDirectory()), cause)
    }
  }

  private class ArgFile @Throws(IOException::class) constructor(private val dynamicVMOptions: Boolean,
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

    fun scheduleWriteFileWhenReady(vmParameters: ParametersList, rememberContent: () -> Unit) {
      myAllPromises.collectResults().onSuccess {
        try {
          writeArgFileNow(vmParameters)
          rememberContent.invoke()
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
    private fun writeArgFileNow(vmParameters: ParametersList) {
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
    private fun pathToUrl(targetPath: String): String {
      val url : URL = if (notEscapeClassPathUrl) {
        // repeat login of `File(path).toURL()` without using system-dependent java.io.File
        URL(URLUtil.FILE_PROTOCOL, "", slashify(targetPath))
      }
      else {
        // repeat logic of `File(path).toURI().toURL()` without using system-dependent java.io.File
        val p = slashify(targetPath)
        URI(URLUtil.FILE_PROTOCOL, null, if (p.startsWith("//")) "//$p" else p, null).toURL()
      }
      return url.toString()
    }

    // counterpart of java.io.File#slashify
    private fun slashify(path: String): String {
      return FileUtil.toSystemIndependentName(path).let {
        if (it.startsWith("/")) it else "/$it"
      }
    }
  }

}
