package com.intellij.openapi.wm;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.Painter;

import javax.swing.*;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.*;

public interface IdeGlassPane {
  void addMousePreprocessor(MouseListener listener, Disposable parent);
  void addMouseMotionPreprocessor(MouseMotionListener listener, Disposable parent);

  void addPainter(final Component component, Painter painter, Disposable parent);
  void removePainter(final Painter painter);


  void removeMousePreprocessor(MouseListener listener);
  void removeMouseMotionPreprocessor(MouseListener listener);
}
