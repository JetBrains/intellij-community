// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar;

import com.intellij.ProjectTopics;
import com.intellij.ide.actions.CopyAction;
import com.intellij.ide.actions.CutAction;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.ide.ui.VirtualFileAppearanceListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionResult;
import com.intellij.openapi.actionSystem.PopupAction;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.AdditionalLibraryRootsListener;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.problems.ProblemListener;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.PsiTreeChangeListener;
import com.intellij.ui.ListActions;
import com.intellij.ui.ScrollingUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.FocusUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 * @deprecated unused in ide.navBar.v2. If you do a change here, please also update v2 implementation
 */
@Deprecated
public final class NavBarListener
  implements ProblemListener, FocusListener, FileStatusListener, AnActionListener, FileEditorManagerListener,
             PsiTreeChangeListener, ModuleRootListener, NavBarModelListener, PropertyChangeListener, KeyListener, WindowFocusListener,
             LafManagerListener, DynamicPluginListener, VirtualFileAppearanceListener, AdditionalLibraryRootsListener {
  private static final String LISTENER = "NavBarListener";
  private final NavBarPanel myPanel;
  private boolean shouldFocusEditor;

  @NotNull
  @Contract(pure=true) // to discourage abandoning the return value
  static Disposable subscribeTo(@NotNull NavBarPanel panel) {
    Disposable disposable = Disposer.newDisposable();
    Disposable old = (Disposable)panel.getClientProperty(LISTENER);
    if (old != null) {
      Disposer.dispose(old);
      panel.putClientProperty(LISTENER, null);
    }

    final NavBarListener listener = new NavBarListener(panel);
    final Project project = panel.getProject();
    if (project.isDisposed()) {
      return disposable;
    }
    panel.putClientProperty(LISTENER, disposable);
    FocusUtil.addFocusOwnerListener(disposable, listener);
    PsiManager.getInstance(project).addPsiTreeChangeListener(listener, disposable);

    MessageBusConnection connection = project.getMessageBus().connect(disposable);
    connection.subscribe(FileStatusListener.TOPIC, listener);
    connection.subscribe(AnActionListener.TOPIC, listener);
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, listener);
    connection.subscribe(AdditionalLibraryRootsListener.TOPIC, listener);
    connection.subscribe(NavBarModelListener.NAV_BAR, listener);
    connection.subscribe(ProblemListener.TOPIC, listener);
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, listener);
    connection.subscribe(DynamicPluginListener.TOPIC, listener);
    connection.subscribe(VirtualFileAppearanceListener.TOPIC, listener);
    panel.addKeyListener(listener);
    Disposer.register(disposable, ()->panel.removeKeyListener(listener));

    if (panel.isInFloatingMode()) {
      Window window = SwingUtilities.windowForComponent(panel);
      if (window != null) {
        window.addWindowFocusListener(listener);
        Disposer.register(disposable, ()->window.removeWindowFocusListener(listener));
      }
    }
    else {
      ApplicationManager.getApplication().getMessageBus().connect(connection).subscribe(LafManagerListener.TOPIC, listener);
    }
    return disposable;
  }

  private NavBarListener(@NotNull NavBarPanel panel) {
    myPanel = panel;
    myPanel.addFocusListener(this);
    if (myPanel.allowNavItemsFocus()) {
      myPanel.addNavBarItemFocusListener(this);
    }
  }

  @Override
  public void focusGained(final FocusEvent e) {
    if (myPanel.allowNavItemsFocus()) {
      // If focus comes from anything in the nav bar panel, ignore the event
      if (UIUtil.isAncestor(myPanel, e.getOppositeComponent())) {
        return;
      }
    }

    if (e.getOppositeComponent() == null && shouldFocusEditor) {
      shouldFocusEditor = false;
      ToolWindowManager.getInstance(myPanel.getProject()).activateEditorComponent();
      return;
    }
    myPanel.updateItems();
    final List<NavBarItem> items = myPanel.getItems();
    if (!myPanel.isInFloatingMode() && !items.isEmpty()) {
      myPanel.setContextComponent(items.get(items.size() - 1));
    } else {
      myPanel.setContextComponent(null);
    }
  }

  @Override
  public void focusLost(final FocusEvent e) {
    if (myPanel.allowNavItemsFocus()) {
      // If focus reaches anything in nav bar panel, ignore the event
      if (UIUtil.isAncestor(myPanel, e.getOppositeComponent())) {
        return;
      }
    }

    if (myPanel.getProject().isDisposed()) {
      myPanel.setContextComponent(null);
      myPanel.hideHint();
      return;
    }
    final DialogWrapper dialog = DialogWrapper.findInstance(e.getOppositeComponent());
    shouldFocusEditor =  dialog != null;
    if (dialog != null) {
      Disposable parent = dialog.getDisposable();
      Disposable onParentDispose = () -> {
        if (dialog.getExitCode() == DialogWrapper.CANCEL_EXIT_CODE) {
          shouldFocusEditor = false;
        }
      };
      if (dialog.isDisposed()) {
        Disposer.dispose(onParentDispose);
      }
      else {
        Disposer.register(parent, onParentDispose);
      }
    }

    // required invokeLater since in current call sequence KeyboardFocusManager is not initialized yet
    // but future focused component
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(() -> processFocusLost(e));
  }

  private void processFocusLost(FocusEvent e) {
    final Component opposite = e.getOppositeComponent();

    if (myPanel.isInFloatingMode() && opposite != null && DialogWrapper.findInstance(opposite) != null) {
      myPanel.hideHint();
      return;
    }

    final boolean nodePopupInactive = !myPanel.isNodePopupActive();
    boolean childPopupInactive = !JBPopupFactory.getInstance().isChildPopupFocused(myPanel);
    if (nodePopupInactive && childPopupInactive) {
      if (opposite != null && opposite != myPanel && !myPanel.isAncestorOf(opposite) && !e.isTemporary()) {
        myPanel.setContextComponent(null);
        myPanel.hideHint();
      }
    }

    myPanel.updateItems();
  }

  private void rebuildUI() {
    if (myPanel.isShowing()) {
      myPanel.getUpdateQueue().queueRebuildUi();
    }
  }

  private void updateModel() {
    if (myPanel.isShowing()) {
      myPanel.getModel().setChanged(true);
      myPanel.getUpdateQueue().queueModelUpdateFromFocus();
    }
  }

  @Override
  public void fileStatusesChanged() {
    rebuildUI();
  }

  @Override
  public void fileStatusChanged(@NotNull VirtualFile virtualFile) {
    rebuildUI();
  }

  @Override
  public void childAdded(@NotNull PsiTreeChangeEvent event) {
    updateModel();
  }

  @Override
  public void childReplaced(@NotNull PsiTreeChangeEvent event) {
    updateModel();
  }

  @Override
  public void childMoved(@NotNull PsiTreeChangeEvent event) {
    updateModel();
  }

  @Override
  public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
    updateModel();
  }

  @Override
  public void propertyChanged(@NotNull final PsiTreeChangeEvent event) {
    updateModel();
  }

  @Override
  public void rootsChanged(@NotNull ModuleRootEvent event) {
    updateModel();
  }

  @Override
  public void libraryRootsChanged(@Nullable @Nls String presentableLibraryName,
                                  @NotNull Collection<? extends VirtualFile> oldRoots,
                                  @NotNull Collection<? extends VirtualFile> newRoots,
                                  @NotNull String libraryNameForDebug) {
    updateModel();
  }

  @Override
  public void problemsAppeared(@NotNull VirtualFile file) {
    updateModel();
  }

  @Override
  public void problemsDisappeared(@NotNull VirtualFile file) {
    updateModel();
  }

  @Override
  public void modelChanged() {
    rebuildUI();
  }

  @Override
  public void selectionChanged() {
    myPanel.updateSelection();
    myPanel.updateItems();
    myPanel.scrollSelectionToVisible(true);
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    if (myPanel.isShowing()) {
      final String name = evt.getPropertyName();
      if ("focusOwner".equals(name) || "permanentFocusOwner".equals(name)) {
        myPanel.getUpdateQueue().restartRebuild();
      }
    }
  }
  @Override
  public void afterActionPerformed(@NotNull AnAction action, @NotNull AnActionEvent event, @NotNull AnActionResult result) {
    if (shouldSkipAction(action)) return;

    if (myPanel.isInFloatingMode()) {
      myPanel.hideHint();
    } else {
      myPanel.cancelPopup();
    }
  }

  private static boolean shouldSkipAction(AnAction action) {
    return action instanceof PopupAction
           || action instanceof CopyAction
           || action instanceof CutAction
           || action instanceof ListActions
           || action instanceof NavBarActions
           || action instanceof ScrollingUtil.ScrollingAction;
  }

  @Override
  public void keyPressed(final KeyEvent e) {
    if (!(e.isAltDown() || e.isMetaDown() || e.isControlDown() || myPanel.isNodePopupActive())) {
      if (!Character.isLetter(e.getKeyChar())) {
        return;
      }

      myPanel.moveDown();
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(() -> {
        try {
          final Robot robot = new Robot();
          final boolean shiftOn = e.isShiftDown();
          final int code = e.getKeyCode();
          if (shiftOn) {
            robot.keyPress(KeyEvent.VK_SHIFT);
          }
          robot.keyPress(code);
          robot.keyRelease(code);
        }
        catch (AWTException ignored) {
        }
      });
    }
  }

  @Override
  public void fileOpened(@NotNull final FileEditorManager manager, @NotNull final VirtualFile file) {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (myPanel.isFocused()) {
        manager.openFile(file, true);
      }
    });
  }

  @Override
  public void lookAndFeelChanged(@NotNull LafManager source) {
    myPanel.getNavBarUI().clearItems();
    myPanel.revalidate();
    myPanel.repaint();
  }

  @Override
  public void virtualFileAppearanceChanged(@NotNull VirtualFile virtualFile) {
    PsiFile psiFile = PsiManager.getInstance(myPanel.getProject()).findFile(virtualFile);
    if (psiFile != null) {
      myPanel.queueFileUpdate(psiFile);
      rebuildUI();
    }
  }

  //---- Ignored
  @Override
  public void windowLostFocus(WindowEvent e) {}

  @Override
  public void windowGainedFocus(WindowEvent e) {}

  @Override
  public void keyTyped(KeyEvent e) {}

  @Override
  public void keyReleased(KeyEvent e) {}

  @Override
  public void beforeChildAddition(@NotNull PsiTreeChangeEvent event) {}

  @Override
  public void beforeChildRemoval(@NotNull PsiTreeChangeEvent event) {}

  @Override
  public void beforeChildReplacement(@NotNull PsiTreeChangeEvent event) {}

  @Override
  public void beforeChildMovement(@NotNull PsiTreeChangeEvent event) {}

  @Override
  public void beforeChildrenChange(@NotNull PsiTreeChangeEvent event) {}

  @Override
  public void beforePropertyChange(@NotNull PsiTreeChangeEvent event) {}

  @Override
  public void childRemoved(@NotNull PsiTreeChangeEvent event) {}

  @Override
  public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
    myPanel.getNavBarUI().clearItems();
  }

  @Override
  public void pluginUnloaded(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
    myPanel.getNavBarUI().clearItems();
  }
}
