// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process.mediator.client

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SystemProperties
import java.io.File
import kotlin.reflect.KClass

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
