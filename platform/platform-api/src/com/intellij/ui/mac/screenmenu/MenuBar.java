// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac.screenmenu;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.Arrays;

@SuppressWarnings("NonPrivateFieldAccessedInSynchronizedContext")
public final class MenuBar extends Menu {
  static private long[] ourLastMenubarPeers = null;
  private Window myFrame;
  private final WindowListener myListener;

  public MenuBar(String title) {
    super(title);
    myListener = new WindowAdapter() {
      @Override
      public void windowActivated(WindowEvent e) {
        if (Arrays.equals(myCachedPeers, ourLastMenubarPeers))
          return;
        refillImpl(true);
      }
    };
  }

  public void setFrame(Window frame) {
    if (myFrame != null) {
      myFrame.removeWindowListener(myListener);
    }
    myFrame = frame;
    if (myFrame != null) {
      myFrame.addWindowListener(myListener);
    }
  }

  @Override
  synchronized void refillImpl(boolean onAppKit) {
    if (myCachedPeers != null) {
      ourLastMenubarPeers = myCachedPeers;
      nativeRefill(0, myCachedPeers, onAppKit);
    }
  }

  @Override
  public synchronized void dispose() {
    setFrame(null);
    super.dispose();
  }
}
