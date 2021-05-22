// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync.dotnet

import com.intellij.openapi.util.text.StringUtilRt
import org.jetbrains.intellij.build.images.IconClassInfo
import org.jetbrains.intellij.build.images.IconsClassGenerator
import org.jetbrains.intellij.build.images.IconsClasses
import org.jetbrains.intellij.build.images.IntellijIconClassGeneratorModuleConfig
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Path

internal class DotnetIconsClasses(override val homePath: String) : IconsClasses() {
  override val modules: List<JpsModule>
    get() = super.modules.filter {
      it.name == "rider-icons"
    }

  override fun generator(home: Path, modules: List<JpsModule>): IconsClassGenerator {
    return object : IconsClassGenerator(home, modules) {
      override fun getIconClassInfo(module: JpsModule, moduleConfig: IntellijIconClassGeneratorModuleConfig?): List<IconClassInfo> {
        val info = super.getIconClassInfo(module, moduleConfig)
        return splitRiderAndReSharper(info.single())
      }

      override fun isInlineClass(name: CharSequence): Boolean {
        // inline redundant classes ReSharperIcons.Resharper and RiderIcons.Rider
        return StringUtilRt.equal(name, "Rider", true) || StringUtilRt.equal(name, "Resharper", true)
      }
    }
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
}