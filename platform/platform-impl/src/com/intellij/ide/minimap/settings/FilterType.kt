// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.settings

import com.intellij.ide.minimap.utils.MiniMessagesBundle
import org.jetbrains.annotations.Nls

enum class FilterType(@Nls val title: String) {
  Nearest(MiniMessagesBundle.message("filter.nearest")),
  Bell(MiniMessagesBundle.message("filter.bell")),
  BiCubic(MiniMessagesBundle.message("filter.bicubic")),
  Box(MiniMessagesBundle.message("filter.box")),
  BSpline(MiniMessagesBundle.message("filter.bspline")),
  Hermite(MiniMessagesBundle.message("filter.hermite")),
  Lanczos(MiniMessagesBundle.message("filter.lanczos")),
  Mitchell(MiniMessagesBundle.message("filter.mitchell")),
  Resample(MiniMessagesBundle.message("filter.resample")),
  Triangle(MiniMessagesBundle.message("filter.triangle"))
}