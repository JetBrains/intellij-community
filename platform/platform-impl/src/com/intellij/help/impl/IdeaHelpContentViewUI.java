package com.intellij.help.impl;

import com.intellij.ide.BrowserUtil;

import javax.help.JHelpContentViewer;
import javax.help.plaf.basic.BasicContentViewerUI;
import javax.swing.*;
import javax.swing.plaf.ComponentUI;
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
      BrowserUtil.launchBrowser(url);
    }else{
      super.linkActivated(u);
    }
  }
}
