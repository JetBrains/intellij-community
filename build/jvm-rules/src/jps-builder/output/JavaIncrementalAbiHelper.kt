package org.jetbrains.bazel.jvm.jps.output

import org.jetbrains.bazel.jvm.hashSet

internal class JavaIncrementalAbiHelper {
  private val classesToBeDeleted = hashSet<String>()

  @Synchronized
  fun createAbiForJava(data: ByteArray): ByteArray {
    return org.jetbrains.bazel.jvm.abi.createAbiForJava(data, classesToBeDeleted) ?: data
  }
}