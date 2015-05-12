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
import com.intellij.diff.comparison.DiffTooBigException;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorNotificationPanel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public abstract class DiffPanelBase extends JPanel implements DataProvider {
  @NotNull protected final JPanel CONTENTS_EQUAL_NOTIFICATION =
    createNotification(DiffBundle.message("diff.contents.are.identical.message.text"));
  @NotNull protected final JPanel CANT_CALCULATE_DIFF =
    createNotification("Can not calculate diff");
  @NotNull protected final JPanel CONTENTS_OPERATION_CANCELED_NOTIFICATION =
    createNotification("Can not calculate diff. Operation canceled.");
  @NotNull protected final JPanel CONTENTS_TOO_BIG_NOTIFICATION =
    createNotification("Can not calculate diff. " + DiffTooBigException.MESSAGE);

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

  public boolean isFocused() {
    return myContext.isFocused();
  }

  public void requestFocus() {
    myContext.requestFocus();
  }

  protected void setCurrentCard(@NotNull String card) {
    setCurrentCard(card, true);
  }

  protected void setCurrentCard(@NotNull String card, boolean keepFocus) {
    boolean restoreFocus = keepFocus && isFocused();

    myCardLayout.show(myContentPanel, card);
    myCurrentCard = card;
    myContentPanel.revalidate();

    if (restoreFocus) requestFocus();
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    return myDataProvider.getData(dataId);
  }

  @Nullable
  public abstract JComponent getPreferredFocusedComponent();

  //
  // Notifications
  //

  public void addContentsEqualNotification() {
    myNotificationsPanel.add(CONTENTS_EQUAL_NOTIFICATION);
    myNotificationsPanel.revalidate();
  }

  public void addTooBigContentNotification() {
    myNotificationsPanel.add(CONTENTS_TOO_BIG_NOTIFICATION);
    myNotificationsPanel.revalidate();
  }

  public void addOperationCanceledNotification() {
    myNotificationsPanel.add(CONTENTS_OPERATION_CANCELED_NOTIFICATION);
    myNotificationsPanel.revalidate();
  }

  public void addDiffErrorNotification() {
    myNotificationsPanel.add(CANT_CALCULATE_DIFF);
    myNotificationsPanel.revalidate();
  }

  public void resetNotifications() {
    myNotificationsPanel.removeAll();
    myNotificationsPanel.revalidate();
  }

  @NotNull
  public static JPanel createNotification(@NotNull String text) {
    return new EditorNotificationPanel().text(text);
  }

  @NotNull
  public static JPanel createNotification(@NotNull String text, @NotNull final Color background) {
    return new EditorNotificationPanel() {
      @Override
      public Color getBackground() {
        return background;
      }
    }.text(text);
  }
}
