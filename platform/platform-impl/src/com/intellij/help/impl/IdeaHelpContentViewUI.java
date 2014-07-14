/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
