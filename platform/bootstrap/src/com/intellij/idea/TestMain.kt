// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("TestMain")
package com.intellij.idea

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginManagerCore.scheduleDescriptorLoading
import com.intellij.platform.ide.bootstrap.ZipFilePoolImpl
import com.intellij.util.lang.PathClassLoader
import com.intellij.util.lang.UrlClassLoader
import com.intellij.util.lang.ZipFilePool
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.file.Path

@ApiStatus.Internal
fun main(rawArgs: Array<String>) {
  val testEntryPointModule = System.getProperty("idea.dev.build.test.entry.point.module")
                             ?: error("idea.dev.build.test.entry.point.module property must be defined")
  val testEntryPointClass = System.getProperty("idea.dev.build.test.entry.point.class")
                            ?: error("idea.dev.build.test.entry.point.class property must be defined")
  val testAdditionalModules = System.getProperty("idea.dev.build.test.additional.modules")
  @Suppress("SSBasedInspection")
  val pluginSet = runBlocking(Dispatchers.Default) {
    val zipFilePoolDeferred = async {
      val result = ZipFilePoolImpl()
      ZipFilePool.POOL = result
      result
    }
    scheduleDescriptorLoading(
      coroutineScope = this@runBlocking,
      zipFilePoolDeferred = zipFilePoolDeferred,
      mainClassLoaderDeferred = CompletableDeferred(PluginManagerCore::class.java.classLoader),
      logDeferred = null,
    ).await()
  }

  val testModule = pluginSet.findEnabledModule(testEntryPointModule) ?: error("module ${testEntryPointModule} not found in product layout")
  val testMainClassLoader = if (!testAdditionalModules.isNullOrEmpty()) {
    PathClassLoader(UrlClassLoader.build().files(testAdditionalModules.split(File.pathSeparator).map(Path::of)).parent(testModule.classLoader))
  }
  else {
    testModule.classLoader
  }
  val testMainClass = testMainClassLoader.loadClass(testEntryPointClass)
  val main = MethodHandles.lookup().findStatic(testMainClass, "main", MethodType.methodType(Void.TYPE, Array<String>::class.java))
  Thread.currentThread().contextClassLoader = testMainClassLoader
  main.invoke(rawArgs)
}