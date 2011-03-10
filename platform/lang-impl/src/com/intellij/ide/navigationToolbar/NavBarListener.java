/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.navigationToolbar;

import com.intellij.ProjectTopics;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.PsiTreeChangeListener;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;

/**
 * @author Konstantin Bulenkov
 */
public class NavBarListener extends WolfTheProblemSolver.ProblemListener
  implements ActionListener, FocusListener, FileStatusListener,
             PsiTreeChangeListener, ModuleRootListener, NavBarModelListener, PropertyChangeListener {
  private static final String LISTENER = "NavBarListener";
  private static final String BUS = "NavBarMessageBus";
  private final NavBarPanel myPanel;

  static void subscribeTo(NavBarPanel panel) {
    if (panel.getClientProperty(LISTENER) != null) {
      unsubscribeFrom(panel);
    }
    final NavBarListener listener = new NavBarListener(panel);
    final Project project = panel.getProject();
    panel.putClientProperty(LISTENER, listener);
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener(listener);
    FileStatusManager.getInstance(project).addFileStatusListener(listener);
    PsiManager.getInstance(project).addPsiTreeChangeListener(listener);
    WolfTheProblemSolver.getInstance(project).addProblemListener(listener);

    final MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, listener);
    connection.subscribe(NavBarModelListener.NAV_BAR, listener);
    panel.putClientProperty(BUS, connection);
  }

  static void unsubscribeFrom(NavBarPanel panel) {
    final NavBarListener listener = (NavBarListener)panel.getClientProperty(LISTENER);
    panel.putClientProperty(LISTENER, null);
    if (listener != null) {
      final Project project = panel.getProject();
      KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener(listener);
      FileStatusManager.getInstance(project).removeFileStatusListener(listener);
      PsiManager.getInstance(project).removePsiTreeChangeListener(listener);
      WolfTheProblemSolver.getInstance(project).removeProblemListener(listener);
      final MessageBusConnection connection = (MessageBusConnection)panel.getClientProperty(BUS);
      panel.putClientProperty(BUS, null);
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  NavBarListener(NavBarPanel panel) {
    myPanel = panel;
    for (NavBarKeyboardCommand command : NavBarKeyboardCommand.values()) {
      registerKey(command);
    }
    myPanel.addFocusListener(this);
  }

  private void registerKey(NavBarKeyboardCommand cmd) {
    myPanel.registerKeyboardAction(this, cmd.name(), cmd.getKeyStroke(), JComponent.WHEN_FOCUSED);
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    final NavBarKeyboardCommand cmd = NavBarKeyboardCommand.fromString(e.getActionCommand());
    if (cmd != null) {
      switch (cmd) {
        case LEFT:     myPanel.moveLeft();  break;
        case RIGHT:    myPanel.moveRight(); break;
        case HOME:     myPanel.moveHome();  break;
        case END:      myPanel.moveEnd();   break;
        case DOWN:     myPanel.moveDown();  break;
        case ENTER:    myPanel.enter();     break;
        case ESCAPE:   myPanel.escape();    break;
        case NAVIGATE: myPanel.navigate();  break;
      }
    }
  }

  public void focusGained(final FocusEvent e) {
    myPanel.updateItems();
    final ArrayList<NavBarItem> items = myPanel.getItems();
    if (!myPanel.isInFloatingMode() && items.size() > 0) {
      myPanel.setContextComponent(items.get(items.size() - 1));
    } else {
      myPanel.setContextComponent(null);
    }
  }

  public void focusLost(final FocusEvent e) {
    if (myPanel.getProject().isDisposed()) {
      myPanel.setContextComponent(null);
      myPanel.hideHint();
      return;
    }

    // required invokeLater since in current call sequence KeyboardFocusManager is not initialized yet
    // but future focused component
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        processFocusLost(e);
      }
    });
  }

  private void processFocusLost(FocusEvent e) {
    final boolean nodePopupInactive = !myPanel.isNodePopupActive();
    boolean childPopupInactive = !JBPopupFactory.getInstance().isChildPopupFocused(myPanel);
    if (nodePopupInactive && childPopupInactive) {
      final Component opposite = e.getOppositeComponent();
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

  public void childAdded(PsiTreeChangeEvent event) {
    updateModel();
  }

  public void childReplaced(PsiTreeChangeEvent event) {
    updateModel();
  }

  public void childMoved(PsiTreeChangeEvent event) {
    updateModel();
  }

  public void childrenChanged(PsiTreeChangeEvent event) {
    updateModel();
  }

  public void propertyChanged(final PsiTreeChangeEvent event) {
    updateModel();
  }

  @Override
  public void rootsChanged(ModuleRootEvent event) {
    updateModel();
  }

  @Override
  public void problemsAppeared(VirtualFile file) {
    updateModel();
  }

  @Override
  public void problemsDisappeared(VirtualFile file) {
    updateModel();
  }

  @Override
  public void modelChanged() {
    rebuildUI();
  }

  @Override
  public void selectionChanged() {
    myPanel.updateItems();
    myPanel.scrollSelectionToVisible();
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

  //---- Ignored
  @Override
  public void beforeRootsChange(ModuleRootEvent event) {}

  @Override
  public void beforeChildAddition(PsiTreeChangeEvent event) {}

  @Override
  public void beforeChildRemoval(PsiTreeChangeEvent event) {}

  @Override
  public void beforeChildReplacement(PsiTreeChangeEvent event) {}

  @Override
  public void beforeChildMovement(PsiTreeChangeEvent event) {}

  @Override
  public void beforeChildrenChange(PsiTreeChangeEvent event) {}

  @Override
  public void beforePropertyChange(PsiTreeChangeEvent event) {}

  @Override
  public void childRemoved(PsiTreeChangeEvent event) {}
}
