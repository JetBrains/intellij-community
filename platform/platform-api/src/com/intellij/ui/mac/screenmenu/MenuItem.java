// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.screenmenu;

import com.intellij.openapi.Disposable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

@SuppressWarnings({"UndesirableClassUsage", "NonPrivateFieldAccessedInSynchronizedContext", "FieldAccessedSynchronizedAndUnsynchronized"})
public class MenuItem implements Disposable {
  long nativePeer;
  Runnable actionDelegate;

  public void setActionDelegate(Runnable actionDelegate) {
    this.actionDelegate = actionDelegate;
  }

  public void setSubmenu(Menu subMenu) {
    ensureNativePeer();
    subMenu.ensureNativePeer();
    nativeSetSubmenu(nativePeer, subMenu.nativePeer);
  }

  public void setState(boolean isToggled) {
    ensureNativePeer();
    nativeSetState(nativePeer, isToggled);
  }

  public void setEnabled(boolean isEnabled) {
    ensureNativePeer();
    nativeSetEnabled(nativePeer, isEnabled);
  }

  public void setLabel(String label, KeyStroke ks) {
    ensureNativePeer();

    // convert (code from native_peer.setLabel)
    char keyChar = ks == null ? 0 : ks.getKeyChar();
    int keyCode = ks == null ? 0 : ks.getKeyCode();
    int modifiers = ks == null ? 0 : ks.getModifiers();
    if (label == null) {
      label = "";
    }
    if (keyChar == KeyEvent.CHAR_UNDEFINED) {
      keyChar = 0;
    }

    nativeSetLabel(nativePeer, label, keyChar, keyCode, modifiers);
  }

  public void setIcon(final Icon icon) {
    int[] bytes = null;
    int w = 0;
    int h = 0;
    if (icon != null && icon.getIconWidth() > 0 && icon.getIconHeight() > 0) {
      w = icon.getIconWidth();
      h = icon.getIconHeight();
      BufferedImage bimg = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
      Graphics2D g = bimg.createGraphics();
      g.setComposite(AlphaComposite.Src);
      icon.paintIcon(null, g, 0, 0);
      g.dispose();
      bytes = ((DataBufferInt)bimg.getRaster().getDataBuffer()).getData();
    }

    ensureNativePeer();
    nativeSetImage(nativePeer, bytes, w, h);
  }

  public void setAcceleratorText(String acceleratorText) {
    ensureNativePeer();
    nativeSetAcceleratorText(nativePeer, acceleratorText);
  }

  synchronized
  void ensureNativePeer() {
    if (nativePeer == 0) {
      nativePeer = nativeCreate(false);
    }
  }

  // uses NSEventModifierFlags
  // see https://developer.apple.com/documentation/appkit/nseventmodifierflags?language=objc
  @SuppressWarnings("unused")
  void handleAction(final int modifiers) {
    // Called from AppKik
    if (actionDelegate != null)
      actionDelegate.run();
  }

  @Override
  synchronized
  public void dispose() {
    if (nativePeer != 0) {
      nativeDispose(nativePeer);
      nativePeer = 0;
    }
  }

  //
  // Native methods
  //

  // Creates native peer (wrapper for NSMenuItem).
  // User must dealloc it via nativeDispose after usage.
  // Can be invoked from any thread.
  private native long nativeCreate(boolean isSeparator);

  // Dealloc native peer.
  // Can be invoked from any thread.
  private native void nativeDispose(long nativePeer);

  // If item was created but wasn't added into any parent menu then all setters can be invoked from any thread.
  private native void nativeSetLabel(long nativePeer, String label, char keyChar, int keyCode, int modifiers);
  private native void nativeSetImage(long nativePeer, int[] buffer, int w, int h);
  private native void nativeSetEnabled(long nativePeer, boolean isEnabled);
  private native void nativeSetAcceleratorText(long nativePeer, String acceleratorText);
  private native void nativeSetState(long nativePeer, boolean isToggled);
  public native void nativeSetSubmenu(long nativePeer, long submenu);
}
