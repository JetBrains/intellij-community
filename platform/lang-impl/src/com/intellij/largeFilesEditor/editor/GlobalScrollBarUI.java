// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.editor;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.components.DefaultScrollBarUI;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

public class GlobalScrollBarUI extends DefaultScrollBarUI {

  private static final Logger LOG = Logger.getInstance(GlobalScrollBar.class);

  private MyMouseAdapter myMouseAdapter;

  public GlobalScrollBarUI() { }

  public GlobalScrollBarUI(int thickness, int thicknessMax, int thicknessMin) {
    super(thickness, thicknessMax, thicknessMin);
    myMouseAdapter = new MyMouseAdapter();
  }

  @Override
  public void installUI(JComponent c) {
    super.installUI(c);
    myScrollBar.addMouseListener(myMouseAdapter);
    myScrollBar.addMouseMotionListener(myMouseAdapter);
  }

  @Override
  public void uninstallUI(JComponent c) {
    super.uninstallUI(c);
    myScrollBar.removeMouseListener(myMouseAdapter);
  }

  private final class MyMouseAdapter extends MouseAdapter {

    private MyMouseAdapter() {
      super();
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      // LOG.warn(e.toString());
    }

    @Override
    public void mousePressed(MouseEvent e) {
      // LOG.warn(e.toString());
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      ((GlobalScrollBar)myScrollBar).fireValueChangedFromOutside();
    }

    @Override
    public void mouseEntered(MouseEvent e) {
      // LOG.warn(e.toString());
    }

    @Override
    public void mouseExited(MouseEvent e) {
      // LOG.warn(e.toString());
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
      // LOG.warn(e.toString());
    }

    @Override
    public void mouseDragged(MouseEvent e) {
      ((GlobalScrollBar)myScrollBar).fireValueChangedFromOutside();
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      // LOG.warn(e.toString());
    }

  }
}
