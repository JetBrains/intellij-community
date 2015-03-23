/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.ui.ShadowAction;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public abstract class ResizeToolWindowAction extends AnAction implements DumbAware {

  private ToolWindow myLastWindow;
  private ToolWindowManager myLastManager;

  protected JLabel myScrollHelper;

  private ToolWindow myToolWindow;

  private boolean myListenerInstalled;

  protected ResizeToolWindowAction() {
  }

  protected ResizeToolWindowAction(String text) {
    super(text);
  }

  protected ResizeToolWindowAction(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  protected ResizeToolWindowAction(ToolWindow toolWindow, String originalAction, JComponent c) {
    myToolWindow = toolWindow;
    new ShadowAction(this, ActionManager.getInstance().getAction(originalAction), c);
  }

  @Override
  public final void update(AnActionEvent e) {
    Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) {
      setDisabled(e);
      return;
    }

    if (!myListenerInstalled) {
      myListenerInstalled = true;
      ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerAdapter() {
        @Override
        public void projectClosed(Project project) {
          setDisabled(null);
        }
      });
    }

    Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (owner == null) {
      setDisabled(e);
      return;
    }

    final Window windowAncestor = SwingUtilities.getWindowAncestor(owner);
    if (!(windowAncestor instanceof IdeFrame) || windowAncestor instanceof IdeFrame.Child) {
      setDisabled(e);
      return;
    }


    ToolWindowManager mgr = ToolWindowManager.getInstance(project);

    ToolWindow window = myToolWindow;

    if (window != null || mgr.getActiveToolWindowId() != null) {
      if (window == null) {
        window = mgr.getToolWindow(mgr.getActiveToolWindowId());
      }

      if (window == null || !window.isAvailable() || !window.isVisible() || window.getType() == ToolWindowType.FLOATING || window.getType() == ToolWindowType.WINDOWED || !window.isActive()) {
        setDisabled(e);
        return;
      }

      update(e, window, mgr);
      if (e.getPresentation().isEnabled()) {
        myLastWindow = window;
        myLastManager = mgr;
      }
      else {
        setDisabled(e);
      }
    }
    else {
      setDisabled(e);
    }
  }

  private void setDisabled(@Nullable AnActionEvent e) {
    if (e != null) {
      e.getPresentation().setEnabled(false);
    }

    myLastWindow = null;
    myLastManager = null;
    myToolWindow = null;
  }

  protected abstract void update(AnActionEvent event, ToolWindow window, ToolWindowManager mgr);

  @Override
  public final void actionPerformed(AnActionEvent e) {
    actionPerformed(e, myLastWindow, myLastManager);
  }

  @Nullable
  private ToolWindowScrollable getScrollable(ToolWindow wnd, boolean isHorizontalStretchingOffered) {
    KeyboardFocusManager mgr = KeyboardFocusManager.getCurrentKeyboardFocusManager();

    Component eachComponent = mgr.getFocusOwner();
    ToolWindowScrollable scrollable = null;
    while (eachComponent != null) {
      if (!SwingUtilities.isDescendingFrom(eachComponent, wnd.getComponent())) break;

      if (eachComponent instanceof ToolWindowScrollable) {
        ToolWindowScrollable eachScrollable = (ToolWindowScrollable)eachComponent;
        if (isHorizontalStretchingOffered) {
          if (eachScrollable.isHorizontalScrollingNeeded()) {
            scrollable = eachScrollable;
            break;
          }
        } else {
          if (eachScrollable.isVerticalScrollingNeeded()) {
            scrollable = eachScrollable;
            break;
          }
        }
      }

      eachComponent = eachComponent.getParent();
    }

    if (scrollable == null) {
      scrollable = new DefaultToolWindowScrollable();
    }

    if (isHorizontalStretchingOffered && scrollable.isHorizontalScrollingNeeded()) return scrollable;
    if (!isHorizontalStretchingOffered && scrollable.isVerticalScrollingNeeded()) return scrollable;

    return null;
  }

  protected abstract void actionPerformed(AnActionEvent e, ToolWindow wnd, ToolWindowManager mgr);

  protected void stretch(ToolWindow wnd, boolean isHorizontalStretching, boolean isIncrementAction) {
    ToolWindowScrollable scrollable = getScrollable(wnd, isHorizontalStretching);
    if (scrollable == null) return;

    ToolWindowAnchor anchor = wnd.getAnchor();
    if (isHorizontalStretching && !anchor.isHorizontal()) {
      incWidth(wnd, scrollable.getNextHorizontalScroll(), (anchor == ToolWindowAnchor.LEFT) == isIncrementAction);
    } else if (!isHorizontalStretching && anchor.isHorizontal()) {
      incHeight(wnd, scrollable.getNextVerticalScroll(), (anchor == ToolWindowAnchor.TOP) != isIncrementAction);  
    }
  }

  private static void incWidth(ToolWindow wnd, int value, boolean isPositive) {
    ((ToolWindowEx)wnd).stretchWidth(isPositive ? value : -value);
  }

  private static void incHeight(ToolWindow wnd, int value, boolean isPositive) {
    ((ToolWindowEx)wnd).stretchHeight(isPositive ? value : -value);
  }

  public static class Left extends ResizeToolWindowAction {

    public Left() {
    }

    public Left(String text) {
      super(text);
    }

    public Left(String text, String description, Icon icon) {
      super(text, description, icon);
    }

    public Left(ToolWindow toolWindow, JComponent c) {
      super(toolWindow, "ResizeToolWindowLeft", c);
    }

    @Override
    protected void update(AnActionEvent event, ToolWindow window, ToolWindowManager mgr) {
      event.getPresentation().setEnabled(!window.getAnchor().isHorizontal());
    }

    @Override
    protected void actionPerformed(AnActionEvent e, ToolWindow wnd, ToolWindowManager mgr) {
      stretch(wnd, true, false);
    }
  }

  public static class Right extends ResizeToolWindowAction {

    public Right() {
    }

    public Right(String text) {
      super(text);
    }

    public Right(String text, String description, Icon icon) {
      super(text, description, icon);
    }

    public Right(ToolWindow toolWindow, JComponent c) {
      super(toolWindow, "ResizeToolWindowRight", c);
    }

    @Override
    protected void update(AnActionEvent event, ToolWindow window, ToolWindowManager mgr) {
      event.getPresentation().setEnabled(!window.getAnchor().isHorizontal());
    }

    @Override
    protected void actionPerformed(AnActionEvent e, ToolWindow wnd, ToolWindowManager mgr) {
      stretch(wnd, true, true);
    }
  }

  public static class Up extends ResizeToolWindowAction {

    public Up() {
    }

    public Up(String text) {
      super(text);
    }

    public Up(String text, String description, Icon icon) {
      super(text, description, icon);
    }

    public Up(ToolWindow toolWindow, JComponent c) {
      super(toolWindow, "ResizeToolWindowUp", c);
    }

    @Override
    protected void update(AnActionEvent event, ToolWindow window, ToolWindowManager mgr) {
      event.getPresentation().setEnabled(window.getAnchor().isHorizontal());
    }

    @Override
    protected void actionPerformed(AnActionEvent e, ToolWindow wnd, ToolWindowManager mgr) {
      stretch(wnd, false, true);
    }
  }

  public static class Down extends ResizeToolWindowAction {

    public Down() {
    }

    public Down(String text) {
      super(text);
    }

    public Down(String text, String description, Icon icon) {
      super(text, description, icon);
    }

    public Down(ToolWindow toolWindow, JComponent c) {
      super(toolWindow, "ResizeToolWindowDown", c);
    }

    @Override
    protected void update(AnActionEvent event, ToolWindow window, ToolWindowManager mgr) {
      event.getPresentation().setEnabled(window.getAnchor().isHorizontal());
    }

    @Override
    protected void actionPerformed(AnActionEvent e, ToolWindow wnd, ToolWindowManager mgr) {
      stretch(wnd, false, false);
    }
  }

  private class DefaultToolWindowScrollable implements ToolWindowScrollable {

    public boolean isHorizontalScrollingNeeded() {
      return true;
    }

    public int getNextHorizontalScroll() {
      return getReferenceSize().width * Registry.intValue("ide.windowSystem.hScrollChars");
    }

    public boolean isVerticalScrollingNeeded() {
      return true;
    }

    public int getNextVerticalScroll() {
      return getReferenceSize().height * Registry.intValue("ide.windowSystem.vScrollChars");
    }
  }

  private Dimension getReferenceSize() {
    if (myScrollHelper == null) {
      if (SwingUtilities.isEventDispatchThread()) {
        myScrollHelper = new JLabel("W");
      } else {
        return new Dimension(1, 1);
      }
    }

    return myScrollHelper.getPreferredSize();
  }
}
