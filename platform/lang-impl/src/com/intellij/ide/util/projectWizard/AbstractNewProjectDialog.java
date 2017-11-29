/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.DialogWrapperPeer;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.impl.welcomeScreen.FlatWelcomeFrame;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.KeyEvent;

/**
 * @author Dennis.Ushakov
 */
public abstract class AbstractNewProjectDialog extends DialogWrapper {
  private Pair<JPanel, JBList<AnAction>> myPair;

  public AbstractNewProjectDialog() {
    super(ProjectManager.getInstance().getDefaultProject());
    init();
  }

  @Override
  protected void init() {
    super.init();
    DialogWrapperPeer peer = getPeer();
    JRootPane pane = peer.getRootPane();
    if (pane != null) {
      JBDimension size = JBUI.size(FlatWelcomeFrame.MAX_DEFAULT_WIDTH, FlatWelcomeFrame.DEFAULT_HEIGHT);
      pane.setMinimumSize(size);
      pane.setPreferredSize(size);
    }
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    DirectoryProjectGenerator[] generators = Extensions.getExtensions(DirectoryProjectGenerator.EP_NAME);
    setTitle(generators.length == 0 ? "Create Project" : "New Project");
    DefaultActionGroup root = createRootStep();
    Disposer.register(getDisposable(), () -> root.removeAll());

    Pair<JPanel, JBList<AnAction>> pair = FlatWelcomeFrame.createActionGroupPanel(root, null, getDisposable());
    JPanel component = pair.first;
    DumbAwareAction.create(e -> close(CANCEL_EXIT_CODE))
      .registerCustomShortcutSet(KeyEvent.VK_ESCAPE, 0, component);
    myPair = pair;
    UiNotifyConnector.doWhenFirstShown(myPair.second, () -> ScrollingUtil.ensureSelectionExists(myPair.second));

    FlatWelcomeFrame.installQuickSearch(pair.second);
    return component;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return FlatWelcomeFrame.getPreferredFocusedComponent(myPair);
  }

  @Nullable
  @Override
  protected JComponent createSouthPanel() {
    return null;
  }

  @NotNull
  @Override
  protected DialogStyle getStyle() {
    return DialogStyle.COMPACT;
  }

  protected abstract DefaultActionGroup createRootStep();

  @Override
  protected String getHelpId() {
    return "create_new_project_dialog";
  }

  @NotNull
  protected Action[] createActions() {
    return new Action[0];
  }

}
