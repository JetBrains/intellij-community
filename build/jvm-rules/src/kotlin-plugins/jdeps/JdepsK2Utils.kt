// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.bazel.kotlin.plugin.jdeps

import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.fir.java.VirtualFileBasedSourceElement
import org.jetbrains.kotlin.load.kotlin.JvmPackagePartSource
import org.jetbrains.kotlin.load.kotlin.KotlinJvmBinarySourceElement
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedContainerSource

internal fun SourceElement.binaryClass(): String? {
  return when (this) {
    is KotlinJvmBinarySourceElement -> binaryClass.location
    is JvmPackagePartSource -> this.knownJvmBinaryClass?.location
    is VirtualFileBasedSourceElement -> this.virtualFile.path
    else -> null
  }
}

internal fun DeserializedContainerSource.binaryClass(): String? {
  return when (this) {
    is JvmPackagePartSource -> this.knownJvmBinaryClass?.location
    is KotlinJvmBinarySourceElement -> binaryClass.location
    else -> null
  }
}
