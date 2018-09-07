/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diff.tools.util;

import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.ui.Divider;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;


public class DiffSplitter extends Splitter {

  @Nullable private Painter myPainter;
  @Nullable private AnAction myTopAction;
  @Nullable private AnAction myBottomAction;

  public DiffSplitter() {
    setDividerWidth(JBUI.scale(Registry.intValue("diff.divider.width")));
  }

  @Override
  protected Divider createDivider() {
    return new DividerImpl() {
      @Override
      public void setOrientation(boolean isVerticalSplit) {
        removeAll();
        setCursor(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));

        List<JComponent> actionComponents = ContainerUtil.list(createActionComponent(myTopAction),
                                                               createActionComponent(myBottomAction));
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
          add(Box.createVerticalStrut(JBUI.scale(20)), bag.nextLine());
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

  @CalledInAwt
  public void setPainter(@Nullable Painter painter) {
    myPainter = painter;
  }

  public void repaintDivider() {
    getDivider().repaint();
  }

  public interface Painter {
    void paint(@NotNull Graphics g, @NotNull JComponent divider);
  }

  @Nullable
  private static JComponent createActionComponent(@Nullable final AnAction action) {
    if (action == null) return null;

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("DiffSplitter", new DefaultActionGroup(action), true);
    toolbar.updateActionsImmediately();
    toolbar.setReservePlaceAutoPopupIcon(false);
    toolbar.getComponent().setCursor(Cursor.getDefaultCursor());
    return toolbar.getComponent();
  }
}
