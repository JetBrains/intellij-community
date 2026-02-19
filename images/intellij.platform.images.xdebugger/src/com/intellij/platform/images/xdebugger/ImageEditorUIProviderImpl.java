// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.images.xdebugger;

import com.intellij.platform.debugger.impl.frontend.frame.ImageEditorUIProvider;
import org.intellij.images.editor.impl.ImageEditorManagerImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import java.awt.GraphicsEnvironment;
import java.awt.Transparency;

class ImageEditorUIProviderImpl implements ImageEditorUIProvider {
  @Override
  public @NotNull JComponent createImageEditorUI(byte @NotNull [] imageData) {
    var icon = new ImageIcon(imageData);

    var w = icon.getIconWidth();
    var h = icon.getIconHeight();
    var image = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration()
      .createCompatibleImage(w, h, Transparency.TRANSLUCENT);
    var g = image.createGraphics();
    try {
      icon.paintIcon(null, g, 0, 0);
    }
    finally {
      g.dispose();
    }
    return ImageEditorManagerImpl.createImageEditorUI(image);
  }
}