// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.db

import com.intellij.ide.IdeBundle
import com.intellij.ide.customize.transferSettings.TransferableKeymapId
import com.intellij.ide.customize.transferSettings.models.BundledKeymap
import com.intellij.ide.customize.transferSettings.models.PluginKeymap
import com.intellij.ide.customize.transferSettings.models.SimpleActionDescriptor
import com.intellij.openapi.actionSystem.KeyboardShortcut
import javax.swing.KeyStroke

// TODO get demo shortcuts from keymap manager
object KnownKeymaps {
  private val VSCodeDemo = listOf(
    SimpleActionDescriptor("SearchEverywhere", IdeBundle.message("transfersettings.label.search.everywhere"), KeyboardShortcut.fromString("shift ctrl A")),
    SimpleActionDescriptor("Debug", IdeBundle.message("transfersettings.label.debug"), KeyboardShortcut(KeyStroke.getKeyStroke("F5"), null)),
    SimpleActionDescriptor("Run", IdeBundle.message("transfersettings.label.run"), KeyboardShortcut.fromString("control F5")),
    SimpleActionDescriptor("BuildSolutionAction", IdeBundle.message("transfersettings.label.build.solution"), KeyboardShortcut.fromString("control shift B"))
  )

  private val VSCodeMacDemo = listOf(
    SimpleActionDescriptor("SearchEverywhere", IdeBundle.message("transfersettings.label.search.everywhere"), KeyboardShortcut.fromString("shift meta A")),
    SimpleActionDescriptor("Debug", IdeBundle.message("transfersettings.label.debug"), KeyboardShortcut(KeyStroke.getKeyStroke("F5"), null)),
    SimpleActionDescriptor("Run", IdeBundle.message("transfersettings.label.run"), KeyboardShortcut.fromString("meta F5")),
    SimpleActionDescriptor("BuildSolutionAction", IdeBundle.message("transfersettings.label.build.solution"), KeyboardShortcut.fromString("meta shift B"))
  )

  private val VSMacDemo = listOf(
    SimpleActionDescriptor("SearchEverywhere", IdeBundle.message("transfersettings.label.search.everywhere"), KeyboardShortcut.fromString("meta PERIOD")),
    SimpleActionDescriptor("GotoDeclaration", IdeBundle.message("transfersettings.label.go.to.declaration"), KeyboardShortcut(KeyStroke.getKeyStroke("meta D"), null)),
    SimpleActionDescriptor("Run", IdeBundle.message("transfersettings.label.run"), KeyboardShortcut.fromString("alt meta ENTER")),
    SimpleActionDescriptor("BuildSolutionAction", IdeBundle.message("transfersettings.label.build.solution"), KeyboardShortcut.fromString("meta B"))
  )

  private val VisualStudio2022Demo = listOf(
    SimpleActionDescriptor("SearchEverywhere", IdeBundle.message("transfersettings.label.search.everywhere"), KeyboardShortcut.fromString("control T")),
    SimpleActionDescriptor("FindUsages", IdeBundle.message("transfersettings.label.find.usages"), KeyboardShortcut.fromString("shift F12")),
    SimpleActionDescriptor("Run", IdeBundle.message("transfersettings.label.run"), KeyboardShortcut.fromString("control F5")),
    SimpleActionDescriptor("BuildSolutionAction", IdeBundle.message("transfersettings.label.build.solution"), KeyboardShortcut.fromString("control shift B"))
  )

  val VSCode: PluginKeymap = PluginKeymap(
    TransferableKeymapId.VsCode,
    IdeBundle.message("transfersettings.product.vscode"),
    "com.intellij.plugins.vscodekeymap",
    "VSCode",
    BundledKeymap.fromManager(TransferableKeymapId.Default, "\$default"),
    VSCodeDemo
  )
  val VSCodeMac: PluginKeymap = PluginKeymap(
    TransferableKeymapId.VsCodeMac,
    IdeBundle.message("transfersettings.product.vscode"),
    "com.intellij.plugins.vscodekeymap",
    "VSCode OSX",
    BundledKeymap.fromManager(TransferableKeymapId.Default, "\$default"),
    VSCodeMacDemo
  )
  val VSMac: PluginKeymap = PluginKeymap(
    TransferableKeymapId.VsForMac,
    IdeBundle.message("transfersettings.product.visual.studio.for.mac"),
    "com.intellij.plugins.visualstudioformackeymap",
    "Visual Studio for Mac",
    BundledKeymap.fromManager(TransferableKeymapId.Default, "\$default"),
    VSMacDemo
  )
  val VisualStudio2022: PluginKeymap = PluginKeymap(
    TransferableKeymapId.VisualStudio2022,
    IdeBundle.message("transfersettings.product.visual.studio"),
    "com.intellij.plugins.visualstudio2022keymap",
    "Visual Studio 2022",
    BundledKeymap.fromManager(TransferableKeymapId.Default, "\$default"),
    VisualStudio2022Demo
  )
}