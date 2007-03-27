package com.intellij.ide.dnd;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.awt.*;

import com.intellij.openapi.Disposable;

public interface DnDAware {

  void processMouseEvent(final MouseEvent e);

  boolean isOverSelection(final Point point);

  @NotNull
  JComponent getComponent();

}
