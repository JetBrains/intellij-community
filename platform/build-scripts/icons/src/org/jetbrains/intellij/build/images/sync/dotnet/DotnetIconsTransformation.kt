// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync.dotnet

import org.jetbrains.intellij.build.images.isImage
import org.jetbrains.intellij.build.images.sync.isAncestorOf
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo

internal object DotnetIconsTransformation {
  internal const val ideaDarkSuffix = "_dark"
  internal val dotnetExpUiDaySuffix = "RiderDay"
  internal val dotnetExpUiNightSuffix = "RiderNight"
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
          .filter { it.isRegularFile() && isImage(it) }
          .map(::DotnetIcon).groupBy(DotnetIcon::name)
          .forEach { (_, icons) ->
            transform(icons, path)
          }
        return FileVisitResult.CONTINUE
      }
    })
  }

  private fun transform(icons: List<DotnetIcon>, rootPath: Path) {
    val transformed = mutableSetOf<DotnetIcon>()
    var iconCompatibleWithRider = false
    icons.filterOnly(dotnetLightSuffices).minWithOrNull(dotnetLightComparator)?.changeSuffix("")?.also {
      transformed += it
      iconCompatibleWithRider = true
    }
    if (iconCompatibleWithRider) {
      icons.filterOnly(dotnetDarkSuffices).minWithOrNull(dotnetDarkComparator)?.changeSuffix(ideaDarkSuffix)?.also {
        transformed += it
      }

      // change icon suffixes after moving to not override original icons
      icons.firstOrNull { it.suffix == dotnetExpUiDaySuffix }?.let {
        return@let it.moveToExpUi(rootPath) // move before changing suffixes to not override original icons
      }?.changeSuffix("")?.also { transformed += it }
      icons.firstOrNull { it.suffix == dotnetExpUiNightSuffix }?.let {
        return@let it.moveToExpUi(rootPath) // move before changing suffixes to not override original icons
      }?.changeSuffix(ideaDarkSuffix)?.also { transformed += it }
    }
    (icons - transformed).forEach(DotnetIcon::delete)
  }

  private fun comparator(suffices: List<String>) = Comparator<DotnetIcon> { i1, i2 ->
    suffices.indexOf(i1.suffix).compareTo(suffices.indexOf(i2.suffix))
  }

  private fun List<DotnetIcon>.filterOnly(suffices: List<String>) = asSequence().filter {
    suffices.contains(it.suffix)
  }

  private fun DotnetIcon.moveToExpUi(rootPath: Path): DotnetIcon {
    if (!rootPath.isAncestorOf(file)) {
      error("Icon $file is not under $rootPath")
    }

    val relativePath = file.relativeTo(rootPath)
    val target = rootPath.resolve("expui").resolve(relativePath).parent
    return this.moveToDir(target)
  }
}