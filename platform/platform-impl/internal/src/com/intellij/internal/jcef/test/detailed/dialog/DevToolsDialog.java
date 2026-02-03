// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// This is a slightly modified version of test 'tests.detailed.MainFrame' from repository https://github.com/JetBrains/jcef.git
package com.intellij.internal.jcef.test.detailed.dialog;

import org.cef.browser.CefBrowser;
import org.jetbrains.annotations.ApiStatus;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.Point;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import javax.swing.JDialog;

@ApiStatus.Internal
public class  DevToolsDialog extends JDialog {
  private final CefBrowser browser_;

  public DevToolsDialog(Frame owner, String title, CefBrowser browser) {
    this(owner, title, browser, null);
  }

  public DevToolsDialog(Frame owner, String title, CefBrowser browser, Point inspectAt) {
    super(owner, title, false);
    browser_ = browser;

    setLayout(new BorderLayout());
    setSize(800, 600);
    setLocation(owner.getLocation().x + 20, owner.getLocation().y + 20);

    browser.openDevTools(inspectAt);

    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentHidden(ComponentEvent e) {
        dispose();
      }
    });
  }

  @Override
  public void dispose() {
    browser_.closeDevTools();
    super.dispose();
  }
}
