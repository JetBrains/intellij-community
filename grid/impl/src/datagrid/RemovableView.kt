package com.intellij.database.datagrid

import javax.swing.JComponent

interface RemovableView {
  val viewComponent: JComponent?
  fun onRemoved()
}