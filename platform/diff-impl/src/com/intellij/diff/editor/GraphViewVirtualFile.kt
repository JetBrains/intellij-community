package com.intellij.diff.editor

import com.intellij.ide.actions.SplitAction
import com.intellij.openapi.util.Key
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.content.Content
import javax.swing.JComponent

class GraphViewVirtualFile(val toolbarsAndTable: JComponent, val getTabNameFunc: () -> String)
    : LightVirtualFile(getTabNameFunc(), GraphViewFileType.INSTANCE, "") {
    companion object {
        @JvmField
        val TabContentId: Key<String> = Key("TabContentId")
        @JvmField
        val GraphVirtualFile: Key<GraphViewVirtualFile> = Key("GraphVirtualFile")
    }

    init {
        this.putUserData(SplitAction.FORBID_TAB_SPLIT, true)
    }

    fun getTabName(): String {
        return getTabNameFunc()
    }
}