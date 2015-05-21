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
import com.intellij.ui.components.panels.Wrapper;
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

  @NotNull private final Wrapper myNorthPanel;
  @NotNull private final Wrapper mySouthPanel;

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
