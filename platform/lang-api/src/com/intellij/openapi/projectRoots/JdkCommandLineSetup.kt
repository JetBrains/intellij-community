// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots

import com.intellij.execution.CantRunException
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
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.encoding.EncodingManager
import com.intellij.util.PathsList
import com.intellij.util.SystemProperties
import com.intellij.util.text.nullize
import java.nio.charset.Charset
import java.nio.charset.IllegalCharsetNameException
import java.nio.charset.StandardCharsets
import java.nio.charset.UnsupportedCharsetException
import java.util.*

internal class JdkCommandLineSetup(private val request: TargetEnvironmentRequest,
                                   private val target: TargetEnvironmentConfiguration?) {

  val commandLine = TargetedCommandLineBuilder(request)
  private val languageRuntime: JavaLanguageRuntimeConfiguration? = target?.runtimes?.findByType(
    JavaLanguageRuntimeConfiguration::class.java)

  private val classPathVolume by lazy { request.createTempVolume() }
  private val agentVolume by lazy { request.createTempVolume() }

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
        JdkUtil.setArgFileParams(this, commandLine, request,
                                 classPathVolume, agentVolume,
                                 languageRuntime, javaParameters, vmParameters, dynamicVMOptions,
                                 dynamicParameters, cs)
        dynamicMainClass = dynamicParameters
      }
      else if (!vmParameters.isExplicitClassPath() && javaParameters.jarPath == null && commandLineWrapperClass != null) {
        if (javaParameters.isUseClasspathJar) {
          JdkUtil.setClasspathJarParams(this, commandLine, request, classPathVolume,
                                        languageRuntime, javaParameters, vmParameters, commandLineWrapperClass,
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
      JdkUtil.appendParamsEncodingClasspath(this, commandLine, request, classPathVolume,
                                            languageRuntime, javaParameters, vmParameters)
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
      val pathValues: List<TargetValue<String>> = getClassPathValues(javaParameters, modulePath)
      val pathSeparator = platform.pathSeparator.toString()
      commandLine.addParameter(TargetValue.composite(pathValues) { values: Collection<String> ->
        values.joinTo(StringBuilder(), pathSeparator).toString()
      })
    }
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

  private val platform = request.targetPlatform.platform

  private fun joinPath(segments: Array<String>) = segments.joinTo(StringBuilder(), platform.fileSeparator.toString()).toString()

  companion object {
    private const val JAVAAGENT = "-javaagent"
    private const val WRAPPER_CLASS = "com.intellij.rt.execution.CommandLineWrapper"

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
}