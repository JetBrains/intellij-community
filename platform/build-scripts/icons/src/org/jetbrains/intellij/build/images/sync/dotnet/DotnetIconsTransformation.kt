// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync.dotnet

import com.intellij.util.io.isFile
import org.jetbrains.intellij.build.images.isImage
import org.jetbrains.intellij.build.images.sync.isAncestorOf
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import kotlin.io.path.relativeTo

internal object DotnetIconsTransformation {
  internal const val ideaDarkSuffix = "_dark"
  private val dotnetExpUiDaySuffix = "RiderDay"
  private val dotnetExpUiNightSuffix = "RiderNight"
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
            transform(icons, path)
          }
        return FileVisitResult.CONTINUE
      }
    })
  }

  private fun transform(icons: List<DotnetIcon>, rootPath: Path) {
    val transformed = ArrayList<DotnetIcon>(2)
    var hasLightIcon = false
    icons.filterOnly(dotnetLightSuffices).minWithOrNull(dotnetLightComparator)?.changeSuffix("")?.also {
      transformed += it
      hasLightIcon = true
    }
    var hasDarkIcon = false
    if (hasRiderDarkPart(icons)) {
      icons.filterOnly(dotnetDarkSuffices).minWithOrNull(dotnetDarkComparator)?.changeSuffix(ideaDarkSuffix)?.also {
        transformed += it
        hasDarkIcon = true
      }
    }

    icons.firstOrNull() { it.suffix == dotnetExpUiDaySuffix }?.changeSuffix("")?.also {
      if (hasLightIcon) {
        it.moveToExpUi(rootPath)
      }
      transformed += it
    }
    icons.firstOrNull() { it.suffix == dotnetExpUiNightSuffix }?.changeSuffix(ideaDarkSuffix)?.also {
      if (hasDarkIcon) {
        it.moveToExpUi(rootPath)
      }
      transformed += it
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

  private fun DotnetIcon.moveToExpUi(rootPath: Path) {
    if (!rootPath.isAncestorOf(file)) {
      error("Icon $file is not under $rootPath")
    }

    val relativePath = file.relativeTo(rootPath)
    val target = rootPath.resolve("expui").resolve(relativePath).parent
    this.moveToDir(target)
  }
}