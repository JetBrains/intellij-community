// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync.dotnet

import org.jetbrains.intellij.build.images.IconsClassGenerator
import org.jetbrains.intellij.build.images.IconsClassInfo
import org.jetbrains.intellij.build.images.IconsClasses
import org.jetbrains.jps.model.module.JpsModule
import java.io.File

internal class DotnetIconsClasses(override val homePath: String) : IconsClasses() {
  override val modules: List<JpsModule>
    get() = super.modules.filter {
      it.name == "rider-icons"
    }

  override fun generator(home: File, modules: List<JpsModule>) =
    object : IconsClassGenerator(home, modules) {
      override fun getIconsClassInfo(module: JpsModule): List<IconsClassInfo> {
        val info = super.getIconsClassInfo(module)
        return splitRiderAndReSharper(info.single())
      }

      override fun appendInnerClass(className: String, answer: StringBuilder, body: String, level: Int) {
        when (className) {
          // inline redundant classes ReSharperIcons.Resharper and RiderIcons.Rider
          "Rider", "Resharper" -> append(answer, body, 0)
          else -> super.appendInnerClass(className, answer, body, level)
        }
      }
    }

  private fun splitRiderAndReSharper(info: IconsClassInfo): List<IconsClassInfo> {
    val rider = extract("rider/", "RiderIcons", info)
    val reSharper = extract("resharper/", "ReSharperIcons", info)
    return listOf(rider, reSharper)
  }

  private fun extract(imageIdPrefix: String, className: String, info: IconsClassInfo) =
    IconsClassInfo(
      info.customLoad, info.packageName,
      className, info.outFile.parent.resolve("$className.java"),
      info.images.filter { it.id.startsWith(imageIdPrefix) }
    )
}