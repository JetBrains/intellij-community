package com.intellij.diff.editor

import com.intellij.ide.actions.SplitAction
import com.intellij.openapi.util.Key
import com.intellij.testFramework.LightVirtualFile
import javax.swing.JComponent

class VCSContentVirtualFile(val toolbarsAndTable: JComponent, val getTabNameFunc: () -> String)
  : LightVirtualFile(getTabNameFunc(), GraphViewFileType.INSTANCE, "") {
  companion object {
    @JvmField
    val TabSelector: Key<() -> Unit> = Key("TabContentId")
  }

  init {
    this.putUserData(SplitAction.FORBID_TAB_SPLIT, true)
  }

  fun getTabName(): String {
    return getTabNameFunc()
  }
}