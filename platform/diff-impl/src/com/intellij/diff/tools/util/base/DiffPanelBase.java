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
package com.intellij.diff.tools.util.base;

import com.intellij.diff.DiffContext;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiCompatibleDataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class DiffPanelBase extends JPanel implements UiCompatibleDataProvider {
  @Nullable protected final Project myProject;
  @NotNull protected final DiffContext myContext;

  @NotNull private final List<JComponent> myPersistentNotifications = new ArrayList<>();
  @NotNull private final List<JComponent> myNotifications = new ArrayList<>();

  @NotNull protected final JPanel myContentPanel;
  @NotNull protected final Wrapper myNotificationsPanel;

  @NotNull private final Wrapper myNorthPanel;
  @NotNull private final Wrapper mySouthPanel;

  @NotNull protected final CardLayout myCardLayout;

  @SuppressWarnings("NotNullFieldNotInitialized") // field initialized in concrete constructors
  @NotNull protected String myCurrentCard;

  public DiffPanelBase(@Nullable Project project, @NotNull DiffContext context) {
    super(new BorderLayout());
    myProject = project;
    myContext = context;

    myCardLayout = new CardLayout();
    myContentPanel = new JPanel(myCardLayout);

    myNotificationsPanel = new Wrapper();
    myNorthPanel = new Wrapper();
    mySouthPanel = new Wrapper();

    add(myContentPanel, BorderLayout.CENTER);
    add(myNorthPanel, BorderLayout.NORTH);
    add(mySouthPanel, BorderLayout.SOUTH);
  }

  public void setTopPanel(@Nullable JComponent component) {
    myNorthPanel.setContent(component);
  }

  public void setBottomPanel(@Nullable JComponent component) {
    mySouthPanel.setContent(component);
  }

  protected void setCurrentCard(@NotNull String card) {
    setCurrentCard(card, true);
  }

  protected void setCurrentCard(@NotNull String card, boolean keepFocus) {
    if (Objects.equals(myCurrentCard, card)) return;

    Runnable task = () -> {
      myCardLayout.show(myContentPanel, card);
      myCurrentCard = card;
      UIUtil.layoutRecursively(myContentPanel);
      myContentPanel.repaint();
    };

    if (keepFocus) {
      DiffUtil.runPreservingFocus(myContext, task);
    }
    else {
      task.run();
    }
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
  }

  //
  // Notifications
  //

  public void setPersistentNotifications(@NotNull List<? extends JComponent> components) {
    myPersistentNotifications.clear();
    myPersistentNotifications.addAll(components);
    updateNotifications();
  }

  public void resetNotifications() {
    myNotifications.clear();
    updateNotifications();
  }

  public void addNotification(@Nullable JComponent notification) {
    if (notification == null) return;
    myNotifications.add(notification);
    updateNotifications();
  }

  private void updateNotifications() {
    List<JComponent> notifications = new ArrayList<>(ContainerUtil.concat(myPersistentNotifications, myNotifications));
    notifications = DiffUtil.wrapEditorNotificationBorders(notifications);
    myNotificationsPanel.setContent(DiffUtil.createStackedComponents(notifications, DiffUtil.TITLE_GAP));
    validate();
    repaint();
  }
}
