// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.mvStore.kotlin

import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.ExternalIntegerKeyDescriptor
import com.intellij.util.io.KeyDescriptor
import org.h2.mvstore.type.DataType
import org.jetbrains.bazel.jvm.mvStore.EnumeratedStringDataType
import org.jetbrains.bazel.jvm.mvStore.EnumeratedStringDataTypeExternalizer
import org.jetbrains.bazel.jvm.mvStore.MvStoreMapFactory
import org.jetbrains.bazel.jvm.mvStore.StringEnumeratedStringExternalizer
import org.jetbrains.bazel.jvm.mvStore.VarIntDataType
import org.jetbrains.kotlin.incremental.IncrementalCompilationContext
import org.jetbrains.kotlin.incremental.storage.DataExternalizerProvider
import org.jetbrains.kotlin.incremental.storage.FqNameExternalizer
import org.jetbrains.kotlin.incremental.storage.JvmClassNameExternalizer
import org.jetbrains.kotlin.incremental.storage.LookupSymbolKeyDescriptor
import org.jetbrains.kotlin.incremental.storage.StringExternalizer
import org.jetbrains.bazel.jvm.mvStore.mvStoreMapFactoryExposer
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.jvm.JvmClassName
import java.io.File

internal fun <T : Any> guessKeyDataType(keyDescriptor: KeyDescriptor<T>, icContext: IncrementalCompilationContext): DataType<T> {
  val mapFactory = mvStoreMapFactoryExposer.get()!!
  val stringEnumerator = mapFactory.getStringEnumerator()

  if (keyDescriptor is DataExternalizerProvider<*>) {
    @Suppress("UNCHECKED_CAST")
    val dataExternalizer = keyDescriptor.getDataExternalizer() as DataExternalizer<T>
    return createKeyDataType(dataExternalizer, icContext) { EnumeratedStringDataType(stringEnumerator, it) }
      ?: throw IllegalArgumentException("Unsupported keyDescriptor: $keyDescriptor (dataExternalizer=$dataExternalizer)")
  }
  else if (keyDescriptor === icContext.fileDescriptorForSourceFiles) {
    @Suppress("UNCHECKED_CAST")
    return EnumeratedStringDataType(stringEnumerator, FileEnumeratedStringExternalizer(mapFactory.getOldPathRelativizer())) as DataType<T>
  }
  else if (keyDescriptor is LookupSymbolKeyDescriptor) {
    // LookupSymbolKey implements Comparable, so we can use it as-is for keys
    return createDataTypeAdapter(keyDescriptor)
  }

  throw IllegalArgumentException("Unsupported keyDescriptor: $keyDescriptor")
}

internal fun <T : Any, R : Any> createKeyDataType(
  externalizer: DataExternalizer<T>,
  icContext: IncrementalCompilationContext,
  enumeratedStringCreator: (EnumeratedStringDataTypeExternalizer<T>) -> DataType<R>,
): DataType<R>? {
  @Suppress("UNCHECKED_CAST")
  return when {
    externalizer === FqNameExternalizer -> {
      enumeratedStringCreator(FqNameEnumeratedStringExternalizer as EnumeratedStringDataTypeExternalizer<T>)
    }

    externalizer === JvmClassNameExternalizer -> {
      enumeratedStringCreator(JvmClassNameEnumeratedStringExternalizer as EnumeratedStringDataTypeExternalizer<T>)
    }

    externalizer === ExternalIntegerKeyDescriptor.INSTANCE -> {
      VarIntDataType
    }

    externalizer === StringExternalizer -> {
      enumeratedStringCreator(StringEnumeratedStringExternalizer as EnumeratedStringDataTypeExternalizer<T>)
    }

    externalizer.javaClass.name == "org.jetbrains.kotlin.incremental.storage.LegacyFqNameExternalizer" -> {
      enumeratedStringCreator(FqNameEnumeratedStringExternalizer as EnumeratedStringDataTypeExternalizer<T>)
    }

    icContext.fileDescriptorForSourceFiles == externalizer || icContext.fileDescriptorForOutputFiles == externalizer -> {
      enumeratedStringCreator(FileEnumeratedStringExternalizer(mvStoreMapFactoryExposer.get()!!.getOldPathRelativizer()) as EnumeratedStringDataTypeExternalizer<T>)
    }

    else -> null
  } as DataType<R>?
}

internal class FileEnumeratedStringExternalizer(
  private val pathConverter: MvStoreMapFactory.LegacyKotlinPathRelativizer,
) : EnumeratedStringDataTypeExternalizer<File> {
  override fun createStorage(size: Int): Array<File?> {
    return arrayOfNulls(size)
  }

  override fun create(id: String): File {
    return pathConverter.toAbsoluteFile(id)
  }

  override fun getStringId(obj: File): String {
    return pathConverter.toRelative(obj)
  }
}

private val emptyFqNames = arrayOfNulls<FqName>(0)

private object FqNameEnumeratedStringExternalizer : EnumeratedStringDataTypeExternalizer<FqName> {
  override fun createStorage(size: Int): Array<FqName?> = if (size == 0) emptyFqNames else arrayOfNulls(size)

  override fun create(id: String) = FqName(id)

  override fun getStringId(obj: FqName) = obj.asString()
}

private val emptyJvmClassNames = arrayOfNulls<JvmClassName>(0)

private object JvmClassNameEnumeratedStringExternalizer : EnumeratedStringDataTypeExternalizer<JvmClassName> {
  override fun createStorage(size: Int): Array<JvmClassName?> = if (size == 0) emptyJvmClassNames else arrayOfNulls(size)

  override fun create(id: String) = JvmClassName.byInternalName(id)

  override fun getStringId(obj: JvmClassName) = obj.internalName
}