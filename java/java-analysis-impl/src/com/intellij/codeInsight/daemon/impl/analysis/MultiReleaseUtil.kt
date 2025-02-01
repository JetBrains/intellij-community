// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("MultiReleaseUtil")
package com.intellij.codeInsight.daemon.impl.analysis

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.regex.Pattern

private const val MAIN = "main"
private val javaVersionPattern: Pattern by lazy { Pattern.compile("java\\d+") }

@Internal
fun areMainAndAdditionalMultiReleaseModules(mainModule: Module, additionalModule: Module): Boolean {
  if (getMainMultiReleaseModule(additionalModule) == mainModule) {
    return true
  }

  // Fallback: Gradle and JPS
  val mainModuleName = mainModule.name
  if (mainModuleName.endsWith(".$MAIN")) {
    val baseModuleName = mainModuleName.substringBeforeLast(MAIN)
    return javaVersionPattern.matcher(additionalModule.name.substringAfter(baseModuleName)).matches()
  }
  return false
}

@Internal
fun getMainMultiReleaseModule(additionalModule: Module): Module? {
  MultiReleaseSupport.EP_NAME.extensionList.forEach {
    val result = it.getMainMultiReleaseModule(additionalModule)
    if (null != result) {
      return result
    }
  }
  return null
}

interface MultiReleaseSupport {
  fun getMainMultiReleaseModule(additionalModule: Module): Module?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<MultiReleaseSupport> = ExtensionPointName("com.intellij.lang.jvm.multiReleaseSupport")
  }
}