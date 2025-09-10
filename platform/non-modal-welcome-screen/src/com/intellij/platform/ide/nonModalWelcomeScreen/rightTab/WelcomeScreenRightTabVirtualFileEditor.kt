package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.nonModalWelcomeScreen.NonModalWelcomeScreenBundle
import org.jetbrains.annotations.Nls
import java.beans.PropertyChangeListener
import javax.swing.JComponent

internal class WelcomeScreenRightTabVirtualFileEditor(private val newProjectFile: WelcomeScreenRightTabVirtualFile) : FileEditor {
  private val userDataHolder: UserDataHolder = UserDataHolderBase()

  init {
    userDataHolder.putUserData(FileEditorManagerKeys.DUMB_AWARE, true)
    userDataHolder.putUserData(FileEditorManagerKeys.FORBID_PREVIEW_TAB, true)
    userDataHolder.putUserData(FileEditorManagerKeys.SINGLETON_EDITOR_IN_WINDOW, true)
  }

  override fun getFile(): VirtualFile = newProjectFile

  override fun getComponent(): JComponent = newProjectFile.window.component

  override fun getPreferredFocusedComponent(): JComponent? = null

  override fun getName(): @Nls(capitalization = Nls.Capitalization.Title) String =
    NonModalWelcomeScreenBundle.message("welcome.screen.editor.name")

  override fun setState(state: FileEditorState) = Unit

  override fun isModified(): Boolean = false

  override fun isValid(): Boolean = true

  override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit

  override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit

  override fun dispose() {
    // TODO
  }

  override fun <T : Any?> getUserData(key: Key<T?>): T? {
    return userDataHolder.getUserData(key)
  }

  override fun <T : Any?> putUserData(key: Key<T?>, value: T?) {
    userDataHolder.putUserData(key, value)
  }
}