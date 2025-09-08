// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileTypes.ex.FakeFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.nonModalWelcomeScreen.NonModalWelcomeScreenBundle
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

internal class WelcomeScreenRightTabVirtualFile(val window: WelcomeScreenRightTab, val project: Project) :
  LightVirtualFile(window.contentProvider.title.get(), NewProjectWindowFileType(window.contentProvider), "") {

  init {
    putUserData(FileEditorManagerKeys.FORBID_TAB_SPLIT, true)
  }

  override fun shouldSkipEventSystem(): Boolean = true

  override fun getPath(): String = name

  private class NewProjectWindowFileType(
    private val contentProvider: WelcomeRightTabContentProvider
  ) : FakeFileType() {
    override fun getName(): @NonNls String = contentProvider.title.get()

    override fun getDescription(): @NlsContexts.Label String =
      NonModalWelcomeScreenBundle.message("welcome.screen.virtual.file.type.description")

    override fun getIcon(): Icon = AllIcons.General.Settings

    override fun isMyFileType(file: VirtualFile): Boolean {
      return file is WelcomeScreenRightTabVirtualFile
    }
  }
}
