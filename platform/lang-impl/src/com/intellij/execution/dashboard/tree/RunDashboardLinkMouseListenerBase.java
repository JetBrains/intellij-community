/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.execution.dashboard.tree;

import com.intellij.execution.dashboard.hyperlink.RunDashboardHyperlinkComponent;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.changes.issueLinks.LinkMouseListenerBase;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author Konstantin Aleev
 */
public abstract class RunDashboardLinkMouseListenerBase extends LinkMouseListenerBase<RunDashboardHyperlinkComponent> {
  private RunDashboardHyperlinkComponent myAimed;

  @Override
  public void mouseMoved(MouseEvent e) {
    Component component = (Component)e.getSource();
    RunDashboardHyperlinkComponent hyperlink = getTagAt(e);
    boolean shouldRepaint = false;

    if (hyperlink != null) {
      UIUtil.setCursor(component, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

      hyperlink.setAimed(true);
      shouldRepaint = myAimed != hyperlink;
      if (myAimed != null && myAimed != hyperlink) {
        myAimed.setAimed(false);
      }
      myAimed = hyperlink;
    }
    else {
      UIUtil.setCursor(component, Cursor.getDefaultCursor());

      if (myAimed != null) {
        myAimed.setAimed(false);
        myAimed = null;
        shouldRepaint = true;
      }
    }

    if (component instanceof JComponent) {
      Object oldValue = UIUtil.getClientProperty(component, RunDashboardHyperlinkComponent.AIMED_OBJECT);
      Object newValue = getAimedObject(e);
      if (!Comparing.equal(oldValue, newValue)) {
        shouldRepaint = true;
        UIUtil.putClientProperty((JComponent)component, RunDashboardHyperlinkComponent.AIMED_OBJECT, newValue);
      }
    }

    if (shouldRepaint) {
      repaintComponent(e);
    }
  }

  protected Object getAimedObject(MouseEvent e) {
    return null;
  }

  protected abstract void repaintComponent(MouseEvent e);

  @Override
  protected void handleTagClick(@Nullable RunDashboardHyperlinkComponent tag, @NotNull MouseEvent e) {
    if (tag != null) {
      tag.onClick(e);
    }
  }

  @Override
  public void installOn(@NotNull Component component) {
    super.installOn(component);

    component.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseExited(MouseEvent e) {
        if (myAimed != null) {
          myAimed.setAimed(false);
          myAimed = null;
          repaintComponent(e);
        }
      }
    });
  }
}
