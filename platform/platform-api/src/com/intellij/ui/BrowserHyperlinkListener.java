package com.intellij.ui;

import com.intellij.ide.BrowserUtil;

import javax.swing.event.HyperlinkListener;
import javax.swing.event.HyperlinkEvent;

/**
 * @author yole
*/
public class BrowserHyperlinkListener implements HyperlinkListener {
  public void hyperlinkUpdate(final HyperlinkEvent e) {
    if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
      BrowserUtil.launchBrowser(e.getDescription());
    }
  }
}
