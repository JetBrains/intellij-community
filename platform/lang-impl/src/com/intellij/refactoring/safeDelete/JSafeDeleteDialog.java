/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.refactoring.safeDelete;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.DeleteUtil;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.ui.StateRestoringCheckBox;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author dsl
 */
public class JSafeDeleteDialog extends SafeDeleteDialog {
  private StateRestoringCheckBox myCbSearchInComments;
  private StateRestoringCheckBox myCbSearchTextOccurrences;

  private JCheckBox myCbSafeDelete;

  JSafeDeleteDialog(Project project, PsiElement[] elements, Callback callback, boolean isDelete) {
    super(project, elements, callback, isDelete);
    setTitle(SafeDeleteHandler.REFACTORING_NAME);
    init();
  }

  @Override
  public boolean isSearchInComments() {
    return myCbSearchInComments.isSelected();
  }

  @Override
  public boolean isSearchForTextOccurences() {
    return needSearchForTextOccurrences() && myCbSearchTextOccurrences.isSelected();
  }


  @Override
  protected boolean isSafeDeleteSelected() {
    return isDelete() && myCbSafeDelete.isSelected();
  }

  @Override
  @NotNull
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  @Override
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("refactoring.safeDelete");
  }

  @Override
  protected JComponent createNorthPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    final GridBagConstraints gbc = new GridBagConstraints();

    final String promptKey = isDelete() ? "prompt.delete.elements" : "search.for.usages.and.delete.elements";
    final String warningMessage = DeleteUtil.generateWarningMessage(IdeBundle.message(promptKey), myElements);

    gbc.insets = JBUI.insets(4, 8);
    gbc.weighty = 1;
    gbc.weightx = 1;
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 2;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.anchor = GridBagConstraints.WEST;
    panel.add(new JLabel(warningMessage), gbc);

    if (isDelete()) {
      gbc.gridy++;
      gbc.gridx = 0;
      gbc.weightx = 0.0;
      gbc.gridwidth = 1;
      gbc.insets = JBUI.insets(4, 8, 0, 8);
      myCbSafeDelete = new JCheckBox(IdeBundle.message("checkbox.safe.delete.with.usage.search"));
      panel.add(myCbSafeDelete, gbc);
      myCbSafeDelete.addActionListener(e -> {
        updateControls(myCbSearchInComments);
        updateControls(myCbSearchTextOccurrences);
      });
    }

    gbc.gridy++;
    gbc.gridx = 0;
    gbc.weightx = 0.0;
    gbc.gridwidth = 1;
    myCbSearchInComments = new StateRestoringCheckBox();
    myCbSearchInComments.setText(RefactoringBundle.getSearchInCommentsAndStringsText());
    panel.add(myCbSearchInComments, gbc);

    if (needSearchForTextOccurrences()) {
      gbc.gridx++;
      myCbSearchTextOccurrences = new StateRestoringCheckBox();
      myCbSearchTextOccurrences.setText(RefactoringBundle.getSearchForTextOccurrencesText());
      panel.add(myCbSearchTextOccurrences, gbc);
    }

    final RefactoringSettings refactoringSettings = RefactoringSettings.getInstance();
    if (myCbSafeDelete != null) {
      myCbSafeDelete.setSelected(refactoringSettings.SAFE_DELETE_WHEN_DELETE);
    }
    myCbSearchInComments.setSelected(myDelegate != null ? myDelegate.isToSearchInComments(myElements[0]) : refactoringSettings.SAFE_DELETE_SEARCH_IN_COMMENTS);
    if (myCbSearchTextOccurrences != null) {
      myCbSearchTextOccurrences.setSelected(myDelegate != null ? myDelegate.isToSearchForTextOccurrences(myElements[0]) : refactoringSettings.SAFE_DELETE_SEARCH_IN_NON_JAVA);
    }
    updateControls(myCbSearchTextOccurrences);
    updateControls(myCbSearchInComments);
    return panel;
  }

  private void updateControls(@Nullable StateRestoringCheckBox checkBox) {
    if (checkBox == null) return;
    if (myCbSafeDelete == null || myCbSafeDelete.isSelected()) {
      checkBox.makeSelectable();
    }
    else {
      checkBox.makeUnselectable(false);
    }
  }

  protected boolean isDelete() {
    return false;
  }

  @Override
  protected JComponent createCenterPanel() {
    return null;
  }
}
