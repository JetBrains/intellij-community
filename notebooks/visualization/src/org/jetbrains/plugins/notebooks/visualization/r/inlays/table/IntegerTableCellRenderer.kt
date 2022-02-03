/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.table

import javax.swing.table.DefaultTableCellRenderer


class IntegerTableCellRenderer : DefaultTableCellRenderer() {
  override fun setValue(value: Any?) {
    @Suppress("HardCodedStringLiteral")
    text = if(value as Int == Int.MIN_VALUE) NULL_VALUE else value.toString()
  }
}
