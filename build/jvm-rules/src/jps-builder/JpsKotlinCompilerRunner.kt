@file:Suppress("PackageDirectoryMismatch")

package org.jetbrains.kotlin.compilerRunner

import org.jetbrains.kotlin.utils.KotlinPaths
import java.io.File

internal object DummyKotlinPaths : KotlinPaths {
  override val homePath: File
    get() = throw kotlin.IllegalStateException()

  override val libPath: File
    get() = throw kotlin.IllegalStateException()

  override fun jar(jar: KotlinPaths.Jar): File = throw kotlin.IllegalStateException()

  override fun klib(jar: KotlinPaths.Jar): File = throw kotlin.IllegalStateException()

  override fun sourcesJar(jar: KotlinPaths.Jar): File? = throw kotlin.IllegalStateException()
}