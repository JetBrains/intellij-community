// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused", "ReplaceJavaStaticMethodWithKotlinAnalog")
@file:JvmName("DevMainImpl")
package org.jetbrains.intellij.build.devServer

import com.intellij.openapi.application.PathManager
import com.intellij.platform.diagnostic.telemetry.exporters.BatchSpanProcessor
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.SystemProperties
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.telemetry.ConsoleSpanExporter
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import org.jetbrains.intellij.build.dev.BuildRequest
import org.jetbrains.intellij.build.dev.buildProductInProcess
import org.jetbrains.intellij.build.dev.getAdditionalPluginMainModules
import org.jetbrains.intellij.build.dev.getIdeSystemProperties
import org.jetbrains.intellij.build.telemetry.traceManagerInitializer
import java.io.File
import java.nio.file.Path

fun buildDevMain(): Collection<Path> {
  //TracerProviderManager.setOutput(Path.of(System.getProperty("user.home"), "trace.json"))
  @Suppress("TestOnlyProblems")
  val ideaProjectRoot = Path.of(PathManager.getHomePathFor(PathManager::class.java)!!)
  System.setProperty("idea.dev.project.root", ideaProjectRoot.toString().replace(File.separator, "/"))

  var homePath: String? = null
  var newClassPath: Collection<Path>? = null
  runBlocking(Dispatchers.Default) {
    val batchSpanProcessorScope = childScope("BatchSpanProcessor")
    val spanProcessor = BatchSpanProcessor(coroutineScope = batchSpanProcessorScope, spanExporters = java.util.List.of(ConsoleSpanExporter()))

    val tracerProvider = SdkTracerProvider.builder()
      .addSpanProcessor(spanProcessor)
      .setResource(Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), "builder")))
      .build()
    try {
      // don't use JaegerJsonSpanExporter - not needed for clients, should be enabled only if needed to avoid writing a ~500KB JSON file
      traceManagerInitializer = {
        val openTelemetry = OpenTelemetrySdk.builder()
          .setTracerProvider(tracerProvider)
          .build()
        val tracer = openTelemetry.getTracer("build-script")
        BuildDependenciesDownloader.TRACER = tracer
        tracer to spanProcessor
      }

      buildProductInProcess(
        BuildRequest(
          platformPrefix = System.getProperty("idea.platform.prefix", "idea"),
          additionalModules = getAdditionalPluginMainModules(),
          projectDir = ideaProjectRoot,
          keepHttpClient = false,
          platformClassPathConsumer = { classPath, runDir ->
            newClassPath = classPath
            homePath = runDir.toString().replace(File.separator, "/")

            @Suppress("SpellCheckingInspection")
            val exceptions = setOf("jna.boot.library.path", "pty4j.preferred.native.folder", "jna.nosys", "jna.noclasspath", "jb.vmOptionsFile")
            val systemProperties = System.getProperties()
            for ((name, value) in getIdeSystemProperties(runDir).map) {
              if (exceptions.contains(name) || !systemProperties.containsKey(name)) {
                systemProperties.setProperty(name, value)
              }
            }
          },
          generateRuntimeModuleRepository = SystemProperties.getBooleanProperty("intellij.build.generate.runtime.module.repository", false),
        )
      )
    }
    finally {
      batchSpanProcessorScope.cancel()
      traceManagerInitializer = { throw IllegalStateException("already built") }
    }
  }
  homePath?.let {
    System.setProperty(PathManager.PROPERTY_HOME_PATH, it)
  }
  return newClassPath!!
}