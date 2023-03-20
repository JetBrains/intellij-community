// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.db

import com.intellij.ide.customize.transferSettings.models.BundledEditorColorScheme

object KnownColorSchemes {
  val Light = BundledEditorColorScheme.fromManager("IntelliJ Light")!! // here should be ok as they are bundled
  val Darcula = BundledEditorColorScheme.fromManager("Darcula")!!
  val HighContrast = BundledEditorColorScheme.fromManager("High contrast")!!
}