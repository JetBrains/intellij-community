// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.bootRuntime

import com.intellij.bootRuntime.command.Command
import java.awt.GridLayout
import javax.swing.JButton
import javax.swing.JPanel

class ActionPanel : JPanel(GridLayout(1, -1, 20,20)) {
  fun substituteActions (actions:List<Command>) {
    removeAll()
    actions.forEach {a -> add(JButton(a))}
  }
}