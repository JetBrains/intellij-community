// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.abi

import androidx.collection.MutableScatterSet
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassWriter

fun createAbiForJava(data: ByteArray, classesToBeDeleted: MutableScatterSet<String>, abiErrorConsumer: (String) -> Unit): ByteArray? {
  val classWriter = ClassWriter(0)
  val abiClassVisitor = JavaAbiClassVisitor(classWriter, classesToBeDeleted, abiErrorConsumer)
  ClassReader(data).accept(abiClassVisitor, ClassReader.SKIP_FRAMES or ClassReader.SKIP_CODE)
  return if (abiClassVisitor.isApiClass) classWriter.toByteArray() else null
}