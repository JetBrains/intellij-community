package com.intellij.openapi.wm;

import com.intellij.openapi.Disposable;

import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

public interface IdeGlassPane {
  void addMousePreprocessor(MouseListener listener, Disposable parent);
  void addMouseMotionPreprocessor(MouseMotionListener listener, Disposable parent);
}
