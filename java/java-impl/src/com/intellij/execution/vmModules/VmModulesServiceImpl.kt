// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.vmModules

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessRunner
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessNotCreatedException
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.containers.CollectionFactory
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentMap

class VmModulesServiceImpl : VmModulesService {
  companion object {
    private val ourData: ConcurrentMap<String, CompletableFuture<List<String>>> = CollectionFactory.createConcurrentSoftValueMap()
  }

  override fun getOrComputeModulesForJdk(javaHome: String): CompletableFuture<List<String>> {
    val future = ourData.computeIfAbsent(javaHome) { CompletableFuture.supplyAsync { computeModules(it) } }
    if (future.isDone) {
      // sometimes the timeout may appear and in order not to block the possibility to get the completion afterwards, it is better to retry
      if (future.get() == null) {
        ourData.remove(javaHome)
      }
    }
    return future
  }

  // when null is returned, it was a timeout
  private fun computeModules(javaHome: String): List<String>? {
    val vmPath = getVmPath(javaHome)
    val generalCommandLine = GeneralCommandLine(vmPath).apply {
      addParameters("--list-modules")
    }
    try {
      val handler = OSProcessHandler(generalCommandLine)
      val runner = CapturingProcessRunner(handler)
      val output = runner.runProcess(1_000)
      if (output.isTimeout) {
        return null
      } else {
        return parse(output.stdout) + parse(output.stderr)
      }
    }
    catch (e: ProcessNotCreatedException) {
      return null
    }
  }

  private fun parse(out: String): List<String> = out.lineSequence().map { line -> line.substringBefore('@') }.toList()

  private fun getVmPath(javaHome: String): String {
    val vmExeName = if (SystemInfo.isWindows) "java.exe" else "java" // do not use JavaW.exe because of issues with encoding
    return Path.of(getConvertedPath(javaHome), "bin", vmExeName).toString()
  }

  private fun getConvertedPath(javaHome: String): String {
    // it is copied from com.intellij.openapi.projectRoots.impl.JavaSdkImpl.getConvertedHomePath
    var systemDependentName = FileUtil.toSystemDependentName(javaHome)
    if (javaHome.endsWith(File.separatorChar)) {
      systemDependentName += File.separatorChar
    }
    return systemDependentName
  }
}