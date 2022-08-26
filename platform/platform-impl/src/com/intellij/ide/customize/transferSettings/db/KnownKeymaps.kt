// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.db

import com.intellij.ide.customize.transferSettings.models.BundledKeymap
import com.intellij.ide.customize.transferSettings.models.PluginKeymap
import com.intellij.ide.customize.transferSettings.models.SimpleActionDescriptor
import com.intellij.openapi.actionSystem.KeyboardShortcut
import javax.swing.KeyStroke

object KnownKeymaps {
  private val VSCodeDemo = listOf(
    SimpleActionDescriptor("SearchEverywhere", "Search Everywhere", KeyboardShortcut.fromString("shift ctrl A")),
    SimpleActionDescriptor("Debug", "Debug", KeyboardShortcut(KeyStroke.getKeyStroke("F5"), null)),
    SimpleActionDescriptor("Run", "Run", KeyboardShortcut.fromString("control F5")),
    SimpleActionDescriptor("BuildSolutionAction", "Build Solution", KeyboardShortcut.fromString("control shift B"))
  )
  val VSCode = PluginKeymap("VSCode", "com.intellij.plugins.vscodekeymap",
                            "VSCode", BundledKeymap.fromManager("\$default"), VSCodeDemo)
}