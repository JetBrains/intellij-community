// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  protected final @Nullable Project myProject;
  protected final @NotNull DiffContext myContext;

  private final @NotNull List<JComponent> myPersistentNotifications = new ArrayList<>();
  private final @NotNull List<JComponent> myNotifications = new ArrayList<>();

  protected final @NotNull JPanel myContentPanel;
  protected final @NotNull Wrapper myNotificationsPanel;

  private final @NotNull Wrapper myNorthPanel;
  private final @NotNull Wrapper mySouthPanel;

  protected final @NotNull CardLayout myCardLayout;

  // field initialized in concrete constructors
  @SuppressWarnings("NotNullFieldNotInitialized") protected @NotNull String myCurrentCard;

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
