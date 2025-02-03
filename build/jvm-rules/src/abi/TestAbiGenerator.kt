// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.abi

import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassWriter
import java.nio.file.Path

internal object TestAbiGenerator {
  @JvmStatic
  fun main(args: Array<String>) {
    val userHomeDir = Path.of(System.getProperty("user.home"))
    val zip = java.util.zip.ZipFile(userHomeDir.resolve("kotlin-worker/ide-impl.jar").toFile())
    try {
      //val className = "com.intellij.codeInsight.folding.impl.FoldingUtil"
      val className = "com.intellij.ui.AppUIUtilKt"
      val zipName = className.replace('.', '/') + ".class"
      val data = zip.getInputStream(zip.getEntry(zipName)).use { it.readAllBytes() }

      val classesToBeDeleted = HashSet<String>()
      val bytes = createAbForKotlin(HashSet(), JarContentToProcess(
        name = zipName.toByteArray(),
        data = data,
        isKotlinModuleMetadata = false,
        isKotlin = true,
      ))
      //val classNode = ClassNode()
      //ClassReader(bytes).accept(classNode, 0)
    }
    finally {
      zip.close()
    }
  }
}