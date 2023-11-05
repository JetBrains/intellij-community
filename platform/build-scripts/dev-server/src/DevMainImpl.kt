// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")
@file:JvmName("DevMainImpl")
package org.jetbrains.intellij.build.devServer

import com.intellij.openapi.application.PathManager
import com.intellij.platform.diagnostic.telemetry.impl.BatchSpanProcessor
import com.intellij.util.childScope
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.ConsoleSpanExporter
import org.jetbrains.intellij.build.TracerProviderManager
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import org.jetbrains.intellij.build.traceManagerInitializer
import java.io.File
import java.nio.file.Path

fun buildDevMain(): Collection<Path> {
  //TracerProviderManager.setOutput(Path.of(System.getProperty("user.home"), "trace.json"))
  val ideaProjectRoot = Path.of(PathManager.getHomePathFor(PathManager::class.java)!!)
  System.setProperty("idea.dev.project.root", ideaProjectRoot.toString().replace(File.separator, "/"))

  var homePath: String? = null
  var newClassPath: Collection<Path>? = null
  runBlocking(Dispatchers.Default) {
    val batchSpanProcessorScope = childScope()
    val spanProcessor = BatchSpanProcessor(coroutineScope = batchSpanProcessorScope, spanExporters = listOf(ConsoleSpanExporter()))

    val tracerProvider = SdkTracerProvider.builder()
      .addSpanProcessor(spanProcessor)
      .setResource(Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), "builder")))
      .build()
    try {
      // don't use JaegerJsonSpanExporter - not needed for clients, should be enabled only if needed to avoid writing ~500KB JSON file
      traceManagerInitializer = {
        val openTelemetry = OpenTelemetrySdk.builder()
          .setTracerProvider(tracerProvider)
          .build()
        val tracer = openTelemetry.getTracer("build-script")
        TracerProviderManager.tracerProvider = tracerProvider
        BuildDependenciesDownloader.TRACER = tracer
        tracer
      }

      buildProductInProcess(BuildRequest(
        platformPrefix = System.getProperty("idea.platform.prefix", "idea"),
        additionalModules = getAdditionalModules()?.toList() ?: emptyList(),
        homePath = ideaProjectRoot,
        keepHttpClient = false,
        platformClassPathConsumer = { classPath, runDir ->
          newClassPath = classPath
          homePath = runDir.toString().replace(File.separator, "/")

          for ((name, value) in getIdeSystemProperties(runDir)) {
            System.setProperty(name, value)
          }
        },
      ))
    }
    finally {
      batchSpanProcessorScope.cancel()
      traceManagerInitializer = { throw IllegalStateException("already built") }
    }
  }
  if (homePath != null) {
    System.setProperty(PathManager.PROPERTY_HOME_PATH, homePath!!)
  }
  return newClassPath!!
}
