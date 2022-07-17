// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.screenmenu;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

public class MenuBar extends Menu {
  private Frame myFrame;
  private WindowListener myListener;

  public MenuBar(String title) {
    super(title);
    myListener = new WindowAdapter() {
      @Override
      public void windowActivated(WindowEvent e) {
        refillImpl(true);
      }
    };
  }

  public void setFrame(Frame frame) {
    if (myFrame != null)
      myFrame.removeWindowListener(myListener);
    myFrame = frame;
    if (myFrame != null)
      myFrame.addWindowListener(myListener);
  }

  @Override
  synchronized void refillImpl(boolean onAppKit) {
    if (myCachedPeers != null)
      nativeRefill(0, myCachedPeers, onAppKit);
  }

  @Override
  public synchronized void dispose() {
    setFrame(null);
    super.dispose();
  }
}
