// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.util

import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.ui.docking.impl.DockManagerImpl
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport

abstract class FileEditorBase : UserDataHolderBase(), FileEditor {

  init {
    configureDefaults()
  }

  private fun configureDefaults() {
    putUserData(FileEditorManager.USE_MAIN_WINDOW, true)
    putUserData(DockManagerImpl.SHOW_NORTH_PANEL, false)
  }

  protected val propertyChangeSupport = PropertyChangeSupport(this)

  override fun dispose() {}
  override fun isValid(): Boolean = true

  override fun selectNotify() {}
  override fun deselectNotify() {}

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {
    propertyChangeSupport.addPropertyChangeListener(listener)
  }

  override fun removePropertyChangeListener(listener: PropertyChangeListener) {
    propertyChangeSupport.removePropertyChangeListener(listener)
  }

  //
  // Unused
  //

  override fun getState(level: FileEditorStateLevel): FileEditorState = FileEditorState.INSTANCE
  override fun setState(state: FileEditorState) {}
  override fun isModified(): Boolean = false

  override fun getBackgroundHighlighter(): BackgroundEditorHighlighter? = null
  override fun getCurrentLocation(): FileEditorLocation? = null
}
