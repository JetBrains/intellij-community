// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util;

import com.intellij.diff.tools.holders.EditorHolder;
import com.intellij.diff.tools.holders.TextEditorHolder;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.ui.Divider;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.MouseEventAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.Arrays;
import java.util.List;


public class DiffSplitter extends JBSplitter {

  private @Nullable Painter myPainter;
  private @Nullable AnAction myTopAction;
  private @Nullable AnAction myBottomAction;

  public DiffSplitter() {
    setDividerWidth(JBUIScale.scale(Registry.intValue("diff.divider.width")));
  }

  @Override
  protected Divider createDivider() {
    return new DividerImpl() {
      @Override
      public void setOrientation(boolean isVerticalSplit) {
        removeAll();
        setCursor(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));

        List<JComponent> actionComponents = Arrays.asList(createActionComponent(myTopAction), createActionComponent(myBottomAction));
        List<JComponent> syncComponents = DiffUtil.createSyncHeightComponents(actionComponents);


        GridBag bag = new GridBag();
        JComponent button1 = syncComponents.get(0);
        JComponent button2 = syncComponents.get(1);

        if (button1 != null) {
          int width = button1.getPreferredSize().width;
          if (getDividerWidth() < width) setDividerWidth(width);
        }
        if (button2 != null) {
          int width = button2.getPreferredSize().width;
          if (getDividerWidth() < width) setDividerWidth(width);
        }


        if (button1 != null) {
          add(button1, bag.nextLine());
        }
        if (button1 != null && button2 != null) {
          add(Box.createVerticalStrut(JBUIScale.scale(20)), bag.nextLine());
        }
        if (button2 != null) {
          add(button2, bag.nextLine());
        }


        revalidate();
        repaint();
      }

      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (myPainter != null) myPainter.paint(g, this);
      }
    };
  }

  public void setTopAction(@Nullable AnAction value) {
    myTopAction = value;
    setOrientation(false);
  }

  public void setBottomAction(@Nullable AnAction value) {
    myBottomAction = value;
    setOrientation(false);
  }

  @RequiresEdt
  public void setPainter(@Nullable Painter painter) {
    myPainter = painter;
  }

  public void repaintDivider() {
    getDivider().repaint();
  }

  public interface Painter {
    void paint(@NotNull Graphics g, @NotNull JComponent divider);
  }

  private static @Nullable JComponent createActionComponent(final @Nullable AnAction action) {
    if (action == null) return null;

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("DiffSplitter", new DefaultActionGroup(action), true);
    toolbar.setTargetComponent(toolbar.getComponent());
    toolbar.setReservePlaceAutoPopupIcon(false);
    toolbar.getComponent().setCursor(Cursor.getDefaultCursor());
    return toolbar.getComponent();
  }

  public void redispatchWheelEventsTo(@Nullable EditorHolder holder) {
    redispatchWheelEventsToDivider(myDivider, holder);
  }

  static void redispatchWheelEventsToDivider(@NotNull JPanel divider, @Nullable EditorHolder holder) {
    if (holder instanceof TextEditorHolder textEditorHolder) {
      divider.addMouseWheelListener(new MouseWheelListener() {
        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
          EditorEx editor = textEditorHolder.getEditor();
          JScrollPane scrollPane = ComponentUtil.getParentOfType(JScrollPane.class, editor.getContentComponent());
          MouseEventAdapter.redispatch(e, scrollPane);
        }
      });
    }
  }
}
