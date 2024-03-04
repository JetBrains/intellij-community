// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.vmModules

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessRunner
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessNotCreatedException
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.CollectionFactory
import java.util.concurrent.*

class VmModulesExecutor {
  companion object {
    private val ourData: ConcurrentMap<String, CompletableFuture<List<String>>> = CollectionFactory.createConcurrentSoftValueMap()
  }

  /**
   * Retrieves or computes the list of jigsaw modules available for the specified JDK.
   *
   * @param module the intellij module for which to retrieve or compute the list of available jigsaw modules
   * @return the list of jigsaw modules available for the specified intellij module or
   *         empty if the intellij module does not contain a JDK or an error has occurred.
   */
  fun getOrComputeModulesForJdk(module: Module): List<String> {
    val sdk = ModuleRootManager.getInstance(module).sdk ?: return listOf()
    if (sdk.sdkType !is JavaSdk || sdk.homePath == null) return listOf()
    try {
      val future = getOrCreateFuture(sdk)
      if (future.isDone) {
        return future.get(ConcurrencyUtil.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
      }
    }
    catch (_: InterruptedException) {
    }
    catch (_: TimeoutException) {
    }
    catch (_: ExecutionException) {
    }
    return listOf()
  }

  private fun getOrCreateFuture(sdk: Sdk): CompletableFuture<List<String>> {
    val sdkHome = sdk.homePath
    var future = ourData.computeIfAbsent(sdkHome) { CompletableFuture.supplyAsync({ computeModules(sdk) }, AppExecutorUtil.getAppExecutorService()) }
    if (future.isDone) {
      // sometimes the timeout may appear, and in order not to block the possibility to get the completion afterwards, it is better to retry
      if (future.get(ConcurrencyUtil.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS) == null) {
        future = ourData.computeIfPresent(sdkHome) { _, value ->
          if (future != value) return@computeIfPresent value // another thread has already changed the value
          return@computeIfPresent CompletableFuture.supplyAsync({ computeModules(sdk) }, AppExecutorUtil.getAppExecutorService())
        }
      }
    }
    return future
  }

  // when null is returned, it was a timeout
  private fun computeModules(sdk: Sdk): List<String>? {
    val vmPath = JavaSdk.getInstance().getVMExecutablePath(sdk)
    val generalCommandLine = GeneralCommandLine(vmPath).apply {
      addParameters("--list-modules")
    }
    try {
      val handler = OSProcessHandler(generalCommandLine)
      val runner = CapturingProcessRunner(handler)
      val output = runner.runProcess(1_000)
      if (output.isTimeout) {
        return null
      }
      else {
        return output.stdout.lineSequence().map { line -> line.substringBefore('@') }.toList()
      }
    }
    catch (e: ProcessNotCreatedException) {
      return null
    }
  }
}