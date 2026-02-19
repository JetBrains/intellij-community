// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UseOptimizedEelFunctions")

package com.intellij.platform.eel

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.util.ServiceLoader

@ApiStatus.Internal
object EelSharedSecrets {
  val filesImpl: EelFilesAccessor =
    ServiceLoader.load(EelFilesAccessor::class.java, EelSharedSecrets::class.java.classLoader).firstOrNull()
    ?: EelFilesAccessor.Default

  @set:TestOnly
  var platformUtilImpl: PlatformUtilAccessor =
    platformUtilAccessors().maxByOrNull { it.priority }
    ?: PlatformUtilAccessor.Default

  fun platformUtilAccessors(): Iterable<PlatformUtilAccessor> =
    ServiceLoader.load(PlatformUtilAccessor::class.java, EelSharedSecrets::class.java.classLoader)

  interface EelFilesAccessor {
    @Throws(IOException::class)
    fun readAllBytes(path: Path): ByteArray

    @Throws(IOException::class)
    fun readString(path: Path, cs: Charset): String

    object Default : EelFilesAccessor {
      override fun readAllBytes(path: Path): ByteArray = Files.readAllBytes(path)

      private val readString =
        try {
          MethodHandles.publicLookup().findStatic(
            Files::class.java,
            "readString",
            MethodType.methodType(String::class.java, Path::class.java, Charset::class.java)
          )
        }
        catch (_: NoSuchMethodException) {  // TODO Test this case
          MethodHandles.publicLookup().findStatic(
            EelSharedSecrets::class.java,
            "readStringObsoleteImpl",
            MethodType.methodType(String::class.java, Path::class.java, Charset::class.java)
          )
        }

      override fun readString(path: Path, cs: Charset): String = readString.invokeExact(path, cs) as String

      @Suppress("unused")  // Used reflectively in this class.
      @JvmStatic
      fun readStringObsoleteImpl(path: Path, cs: Charset): String = String(Files.readAllBytes(path), cs)
    }
  }

  interface PlatformUtilAccessor {
    val priority: Int

    @Throws(IOException::class)
    fun deleteRecursively(fileOrDirectory: Path)

    object Default : PlatformUtilAccessor {
      override val priority: Int = Int.MIN_VALUE

      override fun deleteRecursively(fileOrDirectory: Path) {
        throw UnsupportedOperationException("Not supported in this setup")
      }
    }
  }
}