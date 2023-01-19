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

  private val VSCodeMacDemo = listOf(
    SimpleActionDescriptor("SearchEverywhere", "Search Everywhere", KeyboardShortcut.fromString("shift meta A")),
    SimpleActionDescriptor("Debug", "Debug", KeyboardShortcut(KeyStroke.getKeyStroke("F5"), null)),
    SimpleActionDescriptor("Run", "Run", KeyboardShortcut.fromString("meta F5")),
    SimpleActionDescriptor("BuildSolutionAction", "Build Solution", KeyboardShortcut.fromString("meta shift B"))
  )

  private val VSMacDemo = listOf(
    SimpleActionDescriptor("SearchEverywhere", "Search Everywhere", KeyboardShortcut.fromString("meta PERIOD")),
    SimpleActionDescriptor("GotoDeclaration", "Go to Declaration", KeyboardShortcut(KeyStroke.getKeyStroke("meta D"), null)),
    SimpleActionDescriptor("Run", "Run", KeyboardShortcut.fromString("alt meta ENTER")),
    SimpleActionDescriptor("BuildSolutionAction", "Build Solution", KeyboardShortcut.fromString("meta B"))
  )

  private val VisualStudio2022Demo = listOf(
    SimpleActionDescriptor("SearchEverywhere", "Search Everywhere", KeyboardShortcut.fromString("control T")),
    SimpleActionDescriptor("FindUsages", "Find Usages", KeyboardShortcut.fromString("shift F12")),
    SimpleActionDescriptor("Run", "Run", KeyboardShortcut.fromString("control F5")),
    SimpleActionDescriptor("BuildSolutionAction", "Build Solution", KeyboardShortcut.fromString("control shift B"))
  )

  val VSCode = PluginKeymap("VSCode", "com.intellij.plugins.vscodekeymap",
                            "VSCode", BundledKeymap.fromManager("\$default"), VSCodeDemo)
  val VSCodeMac = PluginKeymap("VSCode", "com.intellij.plugins.vscodekeymap",
                               "VSCode OSX", BundledKeymap.fromManager("\$default"), VSCodeMacDemo)
  val VSMac = PluginKeymap("Visual Studio for Mac", "com.intellij.plugins.visualstudioformackeymap",
                           "Visual Studio for Mac", BundledKeymap.fromManager("\$default"), VSMacDemo)
  val VisualStudio2022 = PluginKeymap("Visual Studio", "com.intellij.plugins.visualstudio2022keymap",
                                      "Visual Studio 2022", BundledKeymap.fromManager("\$default"), VSMacDemo)
}