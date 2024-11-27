package io.bazel.kotlin.plugin.jdeps

import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.fir.java.JavaBinarySourceElement
import org.jetbrains.kotlin.load.kotlin.JvmPackagePartSource
import org.jetbrains.kotlin.load.kotlin.KotlinJvmBinarySourceElement
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedContainerSource

internal fun SourceElement.binaryClass(): String? {
  return when (this) {
    is KotlinJvmBinarySourceElement -> binaryClass.location
    is JvmPackagePartSource -> this.knownJvmBinaryClass?.location
    is JavaBinarySourceElement -> this.javaClass.virtualFile.path
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
