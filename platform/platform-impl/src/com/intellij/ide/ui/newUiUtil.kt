// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.openapi.util.findIconUsingNewImplementation
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
object NotPatchedIconRegistry {
  private val paths = HashSet<Pair<String, ClassLoader?>>()

  fun getData(): List<IconModel> {
    val result = ArrayList<IconModel>(paths.size)
    for ((path, second) in paths) {
      val classLoader = second ?: NotPatchedIconRegistry::class.java.getClassLoader()
      val icon = findIconUsingNewImplementation(path = path, classLoader = classLoader!!, toolTip = null)
      result.add(IconModel(icon, path))
    }
    return result
  }

  fun registerNotPatchedIcon(path: String, classLoader: ClassLoader?) {
    paths.add(Pair(path, classLoader))
  }

  class IconModel(@JvmField var icon: Icon?, @JvmField var originalPath: String) {
    override fun toString(): String = originalPath
  }
}