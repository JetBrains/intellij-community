// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.target

import com.intellij.execution.target.value.TargetValue
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.util.containers.ContainerUtil
import java.io.File
import java.nio.charset.Charset

class TargetedCommandLineBuilder(val request: TargetEnvironmentRequest) : UserDataHolderBase() {
  var exePath: TargetValue<String> = TargetValue.empty()
  private var workingDirectory = TargetValue.empty<FullPathOnTarget>()
  private var inputFilePath = TargetValue.empty<String>()
  var charset: Charset = CharsetToolkit.getDefaultSystemCharset()
  private val parameters: MutableList<TargetValue<out String?>> = ArrayList()
  private val environment: MutableMap<String, TargetValue<String>> = HashMap()
  private val _filesToDeleteOnTermination: MutableSet<File> = HashSet()
  var redirectErrorStream: Boolean = false
  var ptyOptions: PtyOptions? = null

  fun build(): TargetedCommandLine {
    return TargetedCommandLine(exePath, workingDirectory, inputFilePath, charset, parameters.toList(), environment.toMap(),
                               redirectErrorStream, ptyOptions)
  }

  fun setExePath(exePath: String) {
    this.exePath = TargetValue.fixed(exePath)
  }

  /**
   * [workingDirectory] is a path on target
   */
  fun setWorkingDirectory(workingDirectory: TargetValue<FullPathOnTarget>) {
    this.workingDirectory = workingDirectory
  }

  /**
   * @see setWorkingDirectory
   */
  fun setWorkingDirectory(workingDirectory: FullPathOnTarget) {
    this.workingDirectory = TargetValue.fixed(workingDirectory)
  }

  fun getParameters(): List<TargetValue<out String?>> = parameters

  fun addParameter(parameter: TargetValue<out String?>) {
    parameters.add(parameter)
  }

  fun addParameter(parameter: String) {
    parameters.add(TargetValue.fixed(parameter))
  }

  fun addParameters(parametersList: List<String>) {
    for (parameter in parametersList) {
      addParameter(parameter)
    }
  }

  fun addParameters(vararg parametersList: String) {
    for (parameter in parametersList) {
      addParameter(parameter)
    }
  }

  fun addParameterAt(index: Int, parameter: String) {
    addParameterAt(index, TargetValue.fixed(parameter))
  }

  private fun addParameterAt(index: Int, parameter: TargetValue<String?>) {
    parameters.add(index, parameter)
  }

  fun addFixedParametersAt(index: Int, parameters: List<String>) {
    addParametersAt(index, ContainerUtil.map(parameters) { p: String -> TargetValue.fixed(p) })
  }

  fun addParametersAt(index: Int, parameters: List<TargetValue<out String?>>) {
    this.parameters.addAll(index, parameters)
  }

  fun addEnvironmentVariable(name: String, value: TargetValue<String>?) {
    if (value != null) {
      environment[name] = value
    }
    else {
      environment.remove(name)
    }
  }

  fun addEnvironmentVariable(name: String, value: String?) {
    addEnvironmentVariable(name, if (value != null) TargetValue.fixed(value) else null)
  }

  fun removeEnvironmentVariable(name: String) {
    environment.remove(name)
  }

  fun getEnvironmentVariable(name: String): TargetValue<String>? {
    return environment[name]
  }

  fun addFileToDeleteOnTermination(file: File) {
    _filesToDeleteOnTermination.add(file)
  }

  fun setInputFile(inputFilePath: TargetValue<String>) {
    this.inputFilePath = inputFilePath
  }

  val filesToDeleteOnTermination: Set<File>
    get() = _filesToDeleteOnTermination

  fun setRedirectErrorStreamFromRegistry() {
    redirectErrorStream = Registry.`is`("run.processes.with.redirectedErrorStream", false)
  }
}