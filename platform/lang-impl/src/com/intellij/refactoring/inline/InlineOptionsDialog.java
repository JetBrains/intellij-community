/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.refactoring.inline;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.util.RadioUpDownListener;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

public abstract class InlineOptionsDialog extends RefactoringDialog implements InlineOptions {
  protected JRadioButton myRbInlineAll;
  protected JRadioButton myRbInlineThisOnly;
  protected boolean myInvokedOnReference;
  protected final PsiElement myElement;
  private final JLabel myNameLabel = new JLabel();

  protected InlineOptionsDialog(Project project, boolean canBeParent, PsiElement element) {
    super(project, canBeParent);
    myElement = element;
  }

  @Override
  protected JComponent createNorthPanel() {
    myNameLabel.setText(getNameLabelText());
    return myNameLabel;
  }

  @Override
  public boolean isInlineThisOnly() {
    return myRbInlineThisOnly.isSelected();
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel optionsPanel = new JPanel();
    optionsPanel.setBorder(new EmptyBorder(10, 0, 0, 0));
    optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));

    myRbInlineAll = new JRadioButton();
    myRbInlineAll.setText(getInlineAllText());
    myRbInlineAll.setSelected(true);
    myRbInlineThisOnly = new JRadioButton();
    myRbInlineThisOnly.setText(getInlineThisText());

    optionsPanel.add(myRbInlineAll);
    optionsPanel.add(myRbInlineThisOnly);
    ButtonGroup bg = new ButtonGroup();
    bg.add(myRbInlineAll);
    bg.add(myRbInlineThisOnly);
    new RadioUpDownListener(myRbInlineAll, myRbInlineThisOnly);

    myRbInlineThisOnly.setEnabled(myInvokedOnReference);
    final boolean writable = myElement.isWritable();
    myRbInlineAll.setEnabled(writable);
    if(myInvokedOnReference) {
      if (canInlineThisOnly()) {
        myRbInlineAll.setSelected(false);
        myRbInlineAll.setEnabled(false);
        myRbInlineThisOnly.setSelected(true);
      } else {
        if (writable) {
          final boolean inlineThis = isInlineThis();
          myRbInlineThisOnly.setSelected(inlineThis);
          myRbInlineAll.setSelected(!inlineThis);
        }
        else {
          myRbInlineAll.setSelected(false);
          myRbInlineThisOnly.setSelected(true);
        }
      }
    }
    else {
      myRbInlineAll.setSelected(true);
      myRbInlineThisOnly.setSelected(false);
    }

    getPreviewAction().setEnabled(myRbInlineAll.isSelected());
    myRbInlineAll.addItemListener(
      new ItemListener() {
        @Override
        public void itemStateChanged(ItemEvent e) {
          boolean enabled = myRbInlineAll.isSelected();
          getPreviewAction().setEnabled(enabled);
        }
      }
    );
    return optionsPanel;
  }

  protected abstract String getNameLabelText();
  protected abstract String getBorderTitle();
  protected abstract String getInlineAllText();
  protected abstract String getInlineThisText();
  protected abstract boolean isInlineThis();
  protected boolean canInlineThisOnly() {
    return false;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myRbInlineThisOnly.isSelected() ? myRbInlineThisOnly : myRbInlineAll;
  }

  protected static int initOccurrencesNumber(PsiNameIdentifierOwner nameIdentifierOwner) {
    final ProgressManager progressManager = ProgressManager.getInstance();
    final PsiSearchHelper searchHelper = PsiSearchHelper.SERVICE.getInstance(nameIdentifierOwner.getProject());
    final GlobalSearchScope scope = GlobalSearchScope.projectScope(nameIdentifierOwner.getProject());
    final String name = nameIdentifierOwner.getName();
    final boolean isCheapToSearch =
     name != null && searchHelper.isCheapEnoughToSearch(name, scope, null, progressManager.getProgressIndicator()) != PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES;
    return isCheapToSearch ? ReferencesSearch.search(nameIdentifierOwner).findAll().size() : - 1;
  }

}
