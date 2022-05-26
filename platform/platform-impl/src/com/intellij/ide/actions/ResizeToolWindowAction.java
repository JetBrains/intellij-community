// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.internal.statistic.eventLog.events.EventPair;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.FusAwareAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.ui.ShadowAction;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.toolWindow.InternalDecoratorImpl;
import com.intellij.ui.ComponentUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

public abstract class ResizeToolWindowAction extends AnAction implements DumbAware, FusAwareAction {
  private ToolWindow myLastWindow;
  private ToolWindowManager myLastManager;

  protected JLabel myScrollHelper;

  private ToolWindow myToolWindow;

  private boolean myListenerInstalled;

  protected ResizeToolWindowAction() {
  }

  protected ResizeToolWindowAction(@NlsActions.ActionText String text) {
    super(text);
  }

  protected ResizeToolWindowAction(@NlsActions.ActionText String text, @NlsActions.ActionDescription String description, Icon icon) {
    super(text, description, icon);
  }

  protected ResizeToolWindowAction(@NotNull ToolWindow toolWindow, @NonNls String originalAction, JComponent component) {
    myToolWindow = toolWindow;
    new ShadowAction(this, ActionManager.getInstance().getAction(originalAction), component, toolWindow.getDisposable());
  }

  @Override
  public final void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    Editor editor = e.getData(CommonDataKeys.HOST_EDITOR);
    if (editor == null) editor = e.getData(CommonDataKeys.EDITOR);
    boolean isActiveEditorPresented = editor != null && !ConsoleViewUtil.isConsoleViewEditor(editor) && !editor.isViewer();
    if (project == null
        || isActiveEditorPresented
        || ComponentUtil.getParentOfType(InternalDecoratorImpl.class, e.getData(PlatformDataKeys.CONTEXT_COMPONENT)) == null) {
      setDisabled(e);
      return;
    }

    if (!myListenerInstalled) {
      myListenerInstalled = true;
      ApplicationManager.getApplication().getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
        @Override
        public void projectClosed(@NotNull Project project) {
          setDisabled(null);
        }
      });
    }

    Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (owner == null) {
      setDisabled(e);
      return;
    }

    Window windowAncestor = SwingUtilities.getWindowAncestor(owner);
    if (windowAncestor instanceof JWindow) windowAncestor = windowAncestor.getOwner();//SearchEverywhere popup case
    if (!(windowAncestor instanceof IdeFrame) || windowAncestor instanceof IdeFrame.Child) {
      setDisabled(e);
      return;
    }


    ToolWindowManager mgr = ToolWindowManager.getInstance(project);

    ToolWindow window = getToolWindow(project);

    if (window != null) {
      if (!window.isAvailable() || !window.isVisible() || window.getType() == ToolWindowType.FLOATING || window.getType() == ToolWindowType.WINDOWED) {
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

  @Nullable
  private ToolWindow getToolWindow(@NotNull Project project) {
    if (myToolWindow != null) {
      return myToolWindow;
    }
    ToolWindowManager manager = ToolWindowManager.getInstance(project);
    String id = manager.getLastActiveToolWindowId();
    if (id != null) {
      return manager.getToolWindow(id);
    }
    return null;
  }

  private void setDisabled(@Nullable AnActionEvent e) {
    if (e != null) {
      e.getPresentation().setEnabledAndVisible(false);
    }

    myLastWindow = null;
    myLastManager = null;
    myToolWindow = null;
  }

  protected abstract void update(AnActionEvent event, ToolWindow window, ToolWindowManager mgr);

  @Override
  public final void actionPerformed(@NotNull AnActionEvent e) {
    actionPerformed(e, myLastWindow, myLastManager);
  }

  @Override
  public @NotNull List<EventPair<?>> getAdditionalUsageData(@NotNull AnActionEvent event) {
    Project project = event.getProject();
    if (project != null) {
      ToolWindow toolWindow = getToolWindow(project);
      if (toolWindow != null) {
        return Collections.singletonList(ToolwindowFusEventFields.TOOLWINDOW.with(toolWindow.getId()));
      }
    }
    return Collections.emptyList();
  }

  @Nullable
  private ToolWindowScrollable getScrollable(ToolWindow toolWindow, boolean isHorizontalStretchingOffered) {
    Component eachComponent = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    ToolWindowScrollable scrollable = null;
    while (eachComponent != null) {
      if (!SwingUtilities.isDescendingFrom(eachComponent, toolWindow.getComponent())) {
        break;
      }

      if (eachComponent instanceof ToolWindowScrollable) {
        ToolWindowScrollable eachScrollable = (ToolWindowScrollable)eachComponent;
        if (isHorizontalStretchingOffered) {
          if (eachScrollable.isHorizontalScrollingNeeded()) {
            scrollable = eachScrollable;
            break;
          }
        }
        else {
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

    if (isHorizontalStretchingOffered && scrollable.isHorizontalScrollingNeeded() ||
        !isHorizontalStretchingOffered && scrollable.isVerticalScrollingNeeded()) {
      return scrollable;
    }

    return null;
  }

  protected abstract void actionPerformed(AnActionEvent e, ToolWindow toolWindow, ToolWindowManager toolWindowManager);

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

    public Left(@NlsActions.ActionText String text) {
      super(text);
    }

    public Left(@NlsActions.ActionText String text, @NlsActions.ActionDescription String description, Icon icon) {
      super(text, description, icon);
    }

    public Left(@NotNull ToolWindow toolWindow, JComponent c) {
      super(toolWindow, "ResizeToolWindowLeft", c);
    }

    @Override
    protected void update(AnActionEvent event, ToolWindow window, ToolWindowManager mgr) {
      event.getPresentation().setEnabled(!window.getAnchor().isHorizontal());
    }

    @Override
    protected void actionPerformed(AnActionEvent e, ToolWindow toolWindow, ToolWindowManager toolWindowManager) {
      stretch(toolWindow, true, false);
    }
  }

  public static class Right extends ResizeToolWindowAction {
    public Right() {
    }

    public Right(@NlsActions.ActionText String text) {
      super(text);
    }

    public Right(@NlsActions.ActionText String text, @NlsActions.ActionDescription String description, Icon icon) {
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
    protected void actionPerformed(AnActionEvent e, ToolWindow toolWindow, ToolWindowManager toolWindowManager) {
      stretch(toolWindow, true, true);
    }
  }

  public static class Up extends ResizeToolWindowAction {

    public Up() {
    }

    public Up(@NlsActions.ActionText String text) {
      super(text);
    }

    public Up(@NlsActions.ActionText String text, @NlsActions.ActionDescription String description, Icon icon) {
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
    protected void actionPerformed(AnActionEvent e, ToolWindow toolWindow, ToolWindowManager toolWindowManager) {
      stretch(toolWindow, false, true);
    }
  }

  public static class Down extends ResizeToolWindowAction {

    public Down() {
    }

    public Down(@NlsActions.ActionText String text) {
      super(text);
    }

    public Down(@NlsActions.ActionText String text, @NlsActions.ActionDescription String description, Icon icon) {
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
    protected void actionPerformed(AnActionEvent e, ToolWindow toolWindow, ToolWindowManager toolWindowManager) {
      stretch(toolWindow, false, false);
    }
  }

  private class DefaultToolWindowScrollable implements ToolWindowScrollable {

    @Override
    public boolean isHorizontalScrollingNeeded() {
      return true;
    }

    @Override
    public int getNextHorizontalScroll() {
      return getReferenceSize().width * Registry.intValue("ide.windowSystem.hScrollChars");
    }

    @Override
    public boolean isVerticalScrollingNeeded() {
      return true;
    }

    @Override
    public int getNextVerticalScroll() {
      return getReferenceSize().height * Registry.intValue("ide.windowSystem.vScrollChars");
    }
  }

  private Dimension getReferenceSize() {
    if (myScrollHelper == null) {
      if (SwingUtilities.isEventDispatchThread()) {
        myScrollHelper = new JLabel("W"); //NON-NLS
      } else {
        return new Dimension(1, 1);
      }
    }

    return myScrollHelper.getPreferredSize();
  }
}
