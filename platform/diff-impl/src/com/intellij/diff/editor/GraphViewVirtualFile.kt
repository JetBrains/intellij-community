package com.intellij.diff.editor

import com.intellij.testFramework.LightVirtualFile
import javax.swing.JComponent

class GraphViewVirtualFile(val toolbarsAndTable: JComponent) : LightVirtualFile("Repository",
    GraphViewFileType.INSTANCE, "") {
}