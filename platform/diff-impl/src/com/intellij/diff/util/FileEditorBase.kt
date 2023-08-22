// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.util

import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.UserDataHolderBase
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport

abstract class FileEditorBase : UserDataHolderBase(), FileEditor, CheckedDisposable {
  private var isDisposed = false

  override fun isDisposed(): Boolean = isDisposed

  private val propertyChangeSupport = PropertyChangeSupport(this)

  override fun dispose() {
    isDisposed = true
  }

  override fun isValid(): Boolean = true

  fun firePropertyChange(propName: String, oldValue: Boolean, newValue: Boolean) {
    propertyChangeSupport.firePropertyChange(propName, oldValue, newValue)
  }

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
}
