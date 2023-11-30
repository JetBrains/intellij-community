// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui;

import javax.swing.event.ChangeListener;
import javax.swing.text.Caret;
import javax.swing.text.JTextComponent;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public final class EmptyCaret implements Caret {
  public static final Caret INSTANCE = new EmptyCaret();

  @Override
  public void install(JTextComponent c) {
  }

  @Override
  public void deinstall(JTextComponent c) {
  }

  @Override
  public void paint(Graphics g) {
  }

  @Override
  public void addChangeListener(ChangeListener l) {
  }

  @Override
  public void removeChangeListener(ChangeListener l) {
  }

  @Override
  public boolean isVisible() {
    return false;
  }

  @Override
  public void setVisible(boolean v) {
  }

  @Override
  public boolean isSelectionVisible() {
    return false;
  }

  @Override
  public void setSelectionVisible(boolean v) {
  }

  @Override
  public void setMagicCaretPosition(Point p) {
  }

  @Override
  public Point getMagicCaretPosition() {
    return null;
  }

  @Override
  public void setBlinkRate(int rate) {
  }

  @Override
  public int getBlinkRate() {
    return 0;
  }

  @Override
  public int getDot() {
    return 0;
  }

  @Override
  public int getMark() {
    return 0;
  }

  @Override
  public void setDot(int dot) {
  }

  @Override
  public void moveDot(int dot) {
  }
}