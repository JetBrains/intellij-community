/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.usageView.impl;

import com.intellij.ide.DataManager;
import com.intellij.util.OpenSourceUtil;

import javax.swing.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * @author dyoma
 */
public class SelectInEditorHandler {
  public static void installKeyListener(final JComponent component) {
    component.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) selectInEditor(component);
      }
    });
  }

  public static void selectInEditor(final JComponent component) {
    OpenSourceUtil.openSourcesFrom(DataManager.getInstance().getDataContext(component), false);
  }

}
