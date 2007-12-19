package com.intellij.openapi.wm.impl;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.impl.status.StatusBarPatch;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentEvent;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class TestWindowManager extends WindowManagerEx implements ApplicationComponent{
  private static final StatusBar ourStatusBar = new DummyStatusBar();
  
  public final void doNotSuggestAsParent(final Window window) {
  }

  public final Window suggestParentWindow(final Project project) {
    return null;
  }

  public final StatusBar getStatusBar(final Project project) {
    return ourStatusBar;
  }

  public IdeFrame getIdeFrame(final Project project) {
    return null;
  }

  public void setWindowMask(final Window window, final Shape mask) {
  }

  public void resetWindow(final Window window) {
  }

  private static final class DummyStatusBar implements StatusBarEx {
    public final void setInfo(final String s) {}

    public void addFileStatusComponent(final StatusBarPatch component) {

    }

    public void removeFileStatusComponent(final StatusBarPatch component) {

    }

    public void somethingChanged() {

    }

    public void fireNotificationPopup(@NotNull JComponent content, final Color backgroundColor) {
    }

    public final String getInfo() {
      return null;
    }

    public final void clear() {}

    public void addCustomIndicationComponent(@NotNull JComponent c) {}

    public void removeCustomIndicationComponent(@NotNull final JComponent c) {
    }

    public void addFileStatusComponent(final JComponent component, final Runnable onStatusChange) {}
    public void removeFileStatusComponent(final JComponent component) {}

    public void cleanupCustomComponents() {
    }

    public void add(ProgressIndicatorEx indicator, TaskInfo info) {
    }

    public void startRefreshIndication(final String tooltipText) {
    }

    public void stopRefreshIndication() {
    }

    public boolean isProcessWindowOpen() {
      return false;
    }

    public void setProcessWindowOpen(final boolean open) {
    }

    public void update(final Editor editor) {

    }
  }

  public IdeFrameImpl[] getAllFrames() {
    return new IdeFrameImpl[0];
  }

  public final IdeFrameImpl getFrame(final Project project) {
    return null;
  }

  public final IdeFrameImpl allocateFrame(final Project project) {
    throw new UnsupportedOperationException();
  }

  public final void releaseFrame(final IdeFrameImpl frame) {
    throw new UnsupportedOperationException();
  }

  public final Component getFocusedComponent(@NotNull final Window window) {
    throw new UnsupportedOperationException();
  }

  public final Component getFocusedComponent(final Project project) {
    throw new UnsupportedOperationException();
  }

  public final Window getMostRecentFocusedWindow() {
    return null;
  }

  public IdeFrame findFrameFor(@Nullable Project project) {
    throw new UnsupportedOperationException();
  }

  public final CommandProcessor getCommandProcessor() {
    throw new UnsupportedOperationException();
  }

  public final DesktopLayout getLayout() {
    throw new UnsupportedOperationException();
  }

  public final void setLayout(final DesktopLayout layout) {
    throw new UnsupportedOperationException();
  }

  public final void dispatchComponentEvent(final ComponentEvent e) {
    throw new UnsupportedOperationException();
  }

  public final Rectangle getScreenBounds() {
    throw new UnsupportedOperationException();
  }

  public final boolean isInsideScreenBounds(final int x, final int y, final int width) {
    throw new UnsupportedOperationException();
  }

  public final boolean isInsideScreenBounds(final int x, final int y) {
    throw new UnsupportedOperationException();
  }

  public final boolean isAlphaModeSupported() {
    return false;
  }

  public final void setAlphaModeRatio(final Window window, final float ratio) {
    throw new UnsupportedOperationException();
  }

  public final boolean isAlphaModeEnabled(final Window window) {
    throw new UnsupportedOperationException();
  }

  public final void setAlphaModeEnabled(final Window window, final boolean state) {
    throw new UnsupportedOperationException();
  }

  public void hideDialog(JDialog dialog, Project project) {
    dialog.dispose();
  }

  public final String getComponentName() {
    return "TestWindowManager";
  }

  public final void initComponent() { }

  public final void disposeComponent() {
  }
}
