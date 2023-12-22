// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.images.sync.dotnet

import com.intellij.openapi.util.text.StringUtilRt
import org.jetbrains.intellij.build.images.IconClassInfo

internal object DotnetIconClasses {
  fun transformIconClassInfo(riderIconClassInfo: IconClassInfo): List<IconClassInfo> {
    return splitRiderAndReSharper(riderIconClassInfo).removeExpUi()
  }

  fun isInlineClass(name: CharSequence): Boolean {
    // inline redundant classes ReSharperIcons.Resharper and RiderIcons.Rider
    return StringUtilRt.equal(name, "Rider", true) || StringUtilRt.equal(name, "Resharper", true)
  }

  private fun splitRiderAndReSharper(info: IconClassInfo): List<IconClassInfo> {
    val rider = extract("/rider/", "RiderIcons", info)
    val reSharper = extract("/resharper/", "ReSharperIcons", info)
    return listOf(rider, reSharper)
  }

  private fun extract(imageIdPrefix: String, className: String, info: IconClassInfo) =
    IconClassInfo(
      info.customLoad, info.packageName,
      className, info.outFile.parent.resolve("$className.java"),
      info.images.filter { it.id.startsWith(imageIdPrefix) }
    )

  private fun Iterable<IconClassInfo>.removeExpUi() = map { info ->
    IconClassInfo(customLoad = info.customLoad,
                  packageName = info.packageName,
                  className = info.className,
                  outFile = info.outFile,
                  images = info.images.filterNot { it.id.contains("/expui/", true) || it.id.contains("\\expui\\", true) })
  }
}