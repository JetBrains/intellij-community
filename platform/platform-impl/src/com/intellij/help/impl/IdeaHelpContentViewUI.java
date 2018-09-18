// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.help.impl;

import com.intellij.ide.BrowserUtil;
import com.intellij.util.ui.GraphicsUtil;

import javax.help.JHelpContentViewer;
import javax.help.plaf.basic.BasicContentViewerUI;
import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;
import java.net.URL;

/**
 * It a dirty patch! Help system is so ugly that it hangs when it open some "external" links.
 * To prevent this we open "external" links in nornal WEB browser.
 *
 * @author Vladimir Kondratyev
 */
class IdeaHelpContentViewUI extends BasicContentViewerUI{
  /** invoked by reflection */
  public static ComponentUI createUI(JComponent x) {
    return new IdeaHelpContentViewUI((JHelpContentViewer) x);
  }

  public IdeaHelpContentViewUI(JHelpContentViewer contentViewer){
    super(contentViewer);
  }

  @Override
  @SuppressWarnings({"HardCodedStringLiteral"})
  protected void linkActivated(URL u){
    String url=u.toExternalForm();
    if(url.startsWith("http") || url.startsWith("ftp")){
      BrowserUtil.browse(url);
    } else{
      super.linkActivated(u);
    }
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    GraphicsUtil.setupAntialiasing(g);
    super.paint(g, c);
  }
}
