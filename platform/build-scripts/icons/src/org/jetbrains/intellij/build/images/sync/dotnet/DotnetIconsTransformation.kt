// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync.dotnet

import com.intellij.util.io.isFile
import org.jetbrains.intellij.build.images.isImage
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor

internal object DotnetIconsTransformation {
  internal const val ideaDarkSuffix = "_dark"
  /**
   * First icon with one of the suffices (according to order) corresponds to Idea light icon
   */
  internal val dotnetLightSuffices = listOf("RiderLight", "Gray", "Color", "SymbolsVs11Gray", "")
  /**
   * First icon with one of the suffices (according to order) corresponds to Idea dark icon
   */
  internal val dotnetDarkSuffices = listOf("RiderDark", "GrayDark", "SymbolsVs11GrayDark")
  private val dotnetLightComparator = comparator(dotnetLightSuffices)
  private val dotnetDarkComparator = comparator(dotnetDarkSuffices)

  fun transformToIdeaFormat(path: Path) {
    Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
      override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
        if (exc != null) throw exc
        (dir.toFile().listFiles() ?: emptyArray())
          .asSequence().map(File::toPath)
          .filter { it.isFile() && isImage(it) }
          .map(::DotnetIcon).groupBy(DotnetIcon::name)
          .forEach { (_, icons) ->
            transform(icons)
          }
        return FileVisitResult.CONTINUE
      }
    })
  }

  private fun transform(icons: List<DotnetIcon>) {
    val transformed = ArrayList<DotnetIcon>(2)
    icons.filterOnly(dotnetLightSuffices).minWithOrNull(dotnetLightComparator)?.changeSuffix("")?.also {
      transformed += it
    }
    if (hasRiderDarkPart(icons)) {
        icons.filterOnly(dotnetDarkSuffices).minWithOrNull(dotnetDarkComparator)?.changeSuffix(ideaDarkSuffix)?.also {
            transformed += it
        }
    }
    (icons - transformed).forEach(DotnetIcon::delete)
  }

  private fun hasRiderDarkPart(icons: List<DotnetIcon>): Boolean {
      return icons.any { it.suffix == "RiderLight" }.not()
          || (icons.any { it.suffix == "RiderLight" } && icons.any { it.suffix == "RiderDark" })
  }

  private fun comparator(suffices: List<String>) = Comparator<DotnetIcon> { i1, i2 ->
    suffices.indexOf(i1.suffix).compareTo(suffices.indexOf(i2.suffix))
  }

  private fun List<DotnetIcon>.filterOnly(suffices: List<String>) = asSequence().filter {
    suffices.contains(it.suffix)
  }
}