// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync.dotnet

import com.intellij.util.io.createDirectories
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.name

internal class DotnetIcon(val file: Path) {
  class Braces(val start: String, val end: String)
  companion object {
    val BRACES = listOf(Braces("[", "]"), Braces("(", ")"))
  }
  private val extension = "." + file.toFile().extension
  private val fullName = file.fileName.toString().removeSuffix(extension)
  val name: String
  val suffix: String
  override fun toString() = fullName
  override fun hashCode() = file.hashCode()
  override fun equals(other: Any?) = other is DotnetIcon && file.toString() == other.file.toString()

  init {
    var suffix = ""
    var name = fullName
    for (braces in BRACES) {
      val start = fullName.lastIndexOf(braces.start)
      val end = fullName.lastIndexOf(braces.end)
      if (start in 1 until end) {
        name = fullName.removeSuffix(fullName.substring(start..end))
        suffix = fullName.removePrefix(name)
          .removePrefix(braces.start)
          .removeSuffix(braces.end)
        break
      }
    }
    this.name = name
    this.suffix = suffix
  }

  fun changeSuffix(suffix: String) : DotnetIcon {
    if (!Files.exists(file)) error("$file not exist")
    if (this.suffix == suffix) return this
    val name = "$name$suffix$extension"
    val target = file.parent?.resolve(name) ?: Paths.get(name)
    Files.move(file, target, StandardCopyOption.REPLACE_EXISTING)
    return DotnetIcon(target)
  }

  fun moveToDir(targetDir: Path) : DotnetIcon {
    if (!Files.exists(file)) error("$file not exist")
    if (file.parent == targetDir) return this
    val target = targetDir.resolve(file.name)
    if (!targetDir.exists()) targetDir.createDirectories()
    Files.move(file, target, StandardCopyOption.REPLACE_EXISTING)
    return DotnetIcon(target)
  }

  fun delete() {
    if (Files.exists(file)) Files.delete(file)
  }
}
