// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.db

import com.intellij.ide.customize.transferSettings.models.BundledLookAndFeel

object KnownLafs {
  val Light = BundledLookAndFeel.fromManager("IntelliJ Light")
  val Darcula = BundledLookAndFeel.fromManager("Darcula")
  val HighContrast = BundledLookAndFeel.fromManager("High contrast")
}