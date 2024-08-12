// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.util.gotoByName;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class ChooseByNamePanel extends ChooseByNameBase implements Disposable {
  private final JPanel myPanel = new JPanel();
  private final boolean myCheckBoxVisible;

  public ChooseByNamePanel(Project project,
                           ChooseByNameModel model,
                           String initialText,
                           boolean isCheckboxVisible,
                           final PsiElement context) {
    super(project, model, initialText, context);
    myCheckBoxVisible = isCheckboxVisible;
  }

  @Override
  protected void initUI(ChooseByNamePopupComponent.Callback callback, ModalityState modalityState, boolean allowMultipleSelection) {
    super.initUI(callback, modalityState, allowMultipleSelection);

    //myTextFieldPanel.setBorder(new EmptyBorder(0,0,0,0));
    myTextFieldPanel.setBorder(null);

    myPanel.setLayout(new GridBagLayout());

    myPanel.add(myTextFieldPanel, new GridBagConstraints(
      0, 0, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBInsets.emptyInsets(), 0, 0));
    myPanel.add(myListScrollPane, new GridBagConstraints(
      0, 1, 1, 1, 1, 1, GridBagConstraints.WEST, GridBagConstraints.BOTH, JBInsets.emptyInsets(), 0, 0));
  }

  public @NotNull JPanel getPanel() {
    return myPanel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myTextField;
  }

  @Override
  protected void showList() {
  }

  @Override
  protected void hideList() {
  }

  @Override
  protected void close(boolean isOk) {
  }

  @Override
  protected boolean isShowListForEmptyPattern() {
    return true;
  }

  @Override
  protected boolean isCloseByFocusLost() {
    return false;
  }

  @Override
  protected boolean isCheckboxVisible() {
    return myCheckBoxVisible;
  }

  @Override
  public void dispose() {
    setDisposed(true);
    cancelListUpdater();
  }
}
