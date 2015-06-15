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
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public abstract class DiffPanelBase extends JPanel implements DataProvider {
  @Nullable protected final Project myProject;
  @NotNull private final DataProvider myDataProvider;
  @NotNull protected final DiffContext myContext;

  @NotNull protected final JPanel myContentPanel;
  @NotNull protected final JPanel myNotificationsPanel;

  @NotNull protected final CardLayout myCardLayout;

  @NotNull protected String myCurrentCard;

  public DiffPanelBase(@Nullable Project project,
                       @NotNull DataProvider provider,
                       @NotNull DiffContext context) {
    super(new BorderLayout());
    myProject = project;
    myDataProvider = provider;
    myContext = context;

    myCardLayout = new CardLayout();
    myContentPanel = new JPanel(myCardLayout);

    myNotificationsPanel = new JPanel();
    myNotificationsPanel.setLayout(new BoxLayout(myNotificationsPanel, BoxLayout.Y_AXIS));

    add(myContentPanel, BorderLayout.CENTER);

    JComponent topPanel = createTopPanel();
    if (topPanel != null) add(topPanel, BorderLayout.NORTH);

    JComponent bottomPanel = createBottomPanel();
    if (bottomPanel != null) add(bottomPanel, BorderLayout.SOUTH);
  }

  @Nullable
  public JComponent createTopPanel() {
    return null;
  }

  @Nullable
  public JComponent createBottomPanel() {
    return null;
  }

  protected void setCurrentCard(@NotNull String card) {
    setCurrentCard(card, true);
  }

  protected void setCurrentCard(@NotNull String card, boolean keepFocus) {
    boolean restoreFocus = keepFocus && myContext.isFocused();

    myCardLayout.show(myContentPanel, card);
    myCurrentCard = card;
    myContentPanel.revalidate();

    if (restoreFocus) myContext.requestFocus();
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    return myDataProvider.getData(dataId);
  }

  //
  // Notifications
  //

  public void resetNotifications() {
    myNotificationsPanel.removeAll();
    myNotificationsPanel.revalidate();
  }

  public void addNotification(@NotNull JComponent notification) {
    myNotificationsPanel.add(notification);
    myNotificationsPanel.revalidate();
  }
}
