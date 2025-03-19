// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.inline;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public abstract class InlineOptionsWithSearchSettingsDialog extends InlineOptionsDialog {
  protected JCheckBox myCbSearchInComments;
  protected JCheckBox myCbSearchTextOccurences;

  protected InlineOptionsWithSearchSettingsDialog(Project project, boolean canBeParent, PsiElement element) {
    super(project, canBeParent, element);
  }

  protected abstract boolean isSearchInCommentsAndStrings();
  protected abstract void saveSearchInCommentsAndStrings(boolean searchInComments);

  protected abstract boolean isSearchForTextOccurrences();
  protected abstract void saveSearchInTextOccurrences(boolean searchInTextOccurrences);

  @Override
  protected void doAction() {
    final boolean searchInNonJava = myCbSearchTextOccurences.isSelected();
    final boolean searchInComments = myCbSearchInComments.isSelected();
    if (myCbSearchInComments.isEnabled() ) {
      saveSearchInCommentsAndStrings(searchInComments);
    }
    if (myCbSearchTextOccurences.isEnabled()) {
      saveSearchInTextOccurrences(searchInNonJava);
    }
  }

  public void setEnabledSearchSettngs(boolean enabled) {
    myCbSearchInComments.setEnabled(enabled);
    myCbSearchTextOccurences.setEnabled(enabled);
    if (enabled) {
      myCbSearchInComments.setSelected(isSearchInCommentsAndStrings());
      myCbSearchTextOccurences.setSelected(isSearchForTextOccurrences());
    } else {
      myCbSearchInComments.setSelected(false);
      myCbSearchTextOccurences.setSelected(false);
    }
  }

  @Override
  protected @NotNull JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.anchor = GridBagConstraints.NORTH;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 1.0;
    gbc.gridwidth = 2;
    gbc.insets.bottom = JBUIScale.scale(UIUtil.LARGE_VGAP);
    panel.add(super.createCenterPanel(), gbc);

    myCbSearchInComments = new JCheckBox(RefactoringBundle.message("search.in.comments.and.strings"), isSearchInCommentsAndStrings());
    myCbSearchTextOccurences = new JCheckBox(RefactoringBundle.message("search.for.text.occurrences"), isSearchForTextOccurrences());
    gbc.insets.bottom = 0;
    gbc.weightx = 0;
    gbc.weighty = 1;
    gbc.gridwidth = 1;
    gbc.gridy = 1;
    gbc.gridx = 0;
    panel.add(myCbSearchInComments, gbc);
    gbc.insets.left = JBUIScale.scale(UIUtil.DEFAULT_HGAP);
    gbc.gridx = 1;
    panel.add(myCbSearchTextOccurences, gbc);
    final ActionListener actionListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        setEnabledSearchSettngs(!isInlineThisOnly());
      }
    };
    myRbInlineThisOnly.addActionListener(actionListener);
    myRbInlineAll.addActionListener(actionListener);
    if (myKeepTheDeclaration != null) {
      myKeepTheDeclaration.addActionListener(actionListener);
    }
    setEnabledSearchSettngs(!isInlineThisOnly());
    return panel;
  }
}
