// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync.dotnet

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

internal class DotnetIcon(private val file: Path) {
  private val extension = "." + file.toFile().extension
  private val fullName = file.fileName.toString().removeSuffix(extension)
  val name: String
  val suffix: String
  override fun toString() = fullName
  override fun hashCode() = file.hashCode()
  override fun equals(other: Any?) = other is DotnetIcon && file.toString() == other.file.toString()

  init {
    val suffixStart = fullName.lastIndexOf("[")
    val suffixEnd = fullName.lastIndexOf("]")
    name = when (suffixStart) {
      in 1 until suffixEnd -> fullName.substring(suffixStart..suffixEnd)
      else -> ""
    }.let(fullName::removeSuffix)
    suffix = fullName
      .removePrefix(name)
      .removePrefix("[")
      .removeSuffix("]")
  }

  fun changeSuffix(suffix: String) : DotnetIcon {
    if (!Files.exists(file)) error("$file not exist")
    if (this.suffix == suffix) return this
    val name = "$name$suffix$extension"
    val target = file.parent?.resolve(name) ?: Paths.get(name)
    Files.move(file, target, StandardCopyOption.REPLACE_EXISTING)
    return DotnetIcon(target)
  }

  fun delete() {
    if (Files.exists(file)) Files.delete(file)
  }
}
