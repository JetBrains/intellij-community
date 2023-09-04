// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac.screenmenu;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.Arrays;

@SuppressWarnings("NonPrivateFieldAccessedInSynchronizedContext")
public final class MenuBar extends Menu {
  private static long[] ourLastMenubarPeers = null;
  private Window myFrame;
  private final WindowListener myListener;

  public MenuBar(@Nullable String title, @NotNull JFrame frame) {
    super(title);

    setFrame(frame);
    myListener = new WindowAdapter() {
      @Override
      public void windowActivated(WindowEvent e) {
        if (Arrays.equals(myCachedPeers, ourLastMenubarPeers)) {
          return;
        }
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
    if (myCachedPeers != null && myFrame != null && myFrame.isActive()) {
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
