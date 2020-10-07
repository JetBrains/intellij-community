// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator

import com.intellij.execution.process.mediator.daemon.ProcessMediatorServerService
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SystemProperties
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import org.jetbrains.annotations.TestOnly
import java.io.File
import java.util.*
import kotlin.reflect.KClass

@TestOnly
internal fun createInProcessChannelForTesting(bindName: String = "testing"): ManagedChannel {
  return InProcessChannelBuilder.forName(bindName)
    .directExecutor()
    .build()
}

@TestOnly
internal fun createInProcessServerForTesting(bindName: String = "testing"): Server {
  return InProcessServerBuilder.forName(bindName)
    .directExecutor()
    .addService(ProcessMediatorServerService.createServiceDefinition())
    .build()
}

internal fun createProcessBuilderForJavaClass(kClass: KClass<*>): ProcessBuilder {
  return createProcessBuilderForJavaClass(kClass.java)
}

internal fun createProcessBuilderForJavaClass(javaClass: Class<*>): ProcessBuilder {
  val classpathClasses = listOf(javaClass, KotlinVersion::class.java)
  val classpath = classpathClasses.mapNotNullTo(LinkedHashSet()) { it.getResourcePath() }.joinToString(File.pathSeparator)
  return ProcessBuilder(javaVmExecutablePath, "-cp", classpath, javaClass.name)
}

internal val javaVmExecutablePath = SystemProperties.getJavaHome() + File.separator + "bin" + File.separator + "java"
internal fun Class<*>.getResourcePath(): String? {
  return FileUtil.toCanonicalPath(PathManager.getResourceRoot(this, "/" + name.replace('.', '/') + ".class"))
}
