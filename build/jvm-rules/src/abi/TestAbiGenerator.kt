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
      val classReader = ClassReader(zip.getInputStream(zip.getEntry(className.replace('.', '/') + ".class")))
      val classesToBeDeleted = HashSet<String>()
      val classWriter = ClassWriter(classReader, 0)
      val abiVisitor = KotlinAbiClassVisitor(
        classVisitor = classWriter,
        classesToBeDeleted = classesToBeDeleted,
        treatInternalAsPrivate = false,
      )
      classReader.accept(abiVisitor, ClassReader.SKIP_CODE or ClassReader.SKIP_FRAMES)
      val bytes = classWriter.toByteArray()
      //val classNode = ClassNode()
      //ClassReader(bytes).accept(classNode, 0)
    }
    finally {
      zip.close()
    }
  }
}