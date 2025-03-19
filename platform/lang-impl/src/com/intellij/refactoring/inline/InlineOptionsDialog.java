// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.inline;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts.BorderTitle;
import com.intellij.openapi.util.NlsContexts.Label;
import com.intellij.openapi.util.NlsContexts.RadioButton;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.util.RadioUpDownListener;
import com.intellij.util.Query;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.function.Function;
import java.util.function.Predicate;

public abstract class InlineOptionsDialog extends RefactoringDialog implements InlineOptions {
  protected JRadioButton myRbInlineAll;
  protected @Nullable JRadioButton myKeepTheDeclaration;
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
  public boolean isKeepTheDeclaration() {
    if (myKeepTheDeclaration != null) {
      return myKeepTheDeclaration.isSelected();
    }
    return false;
  }

  @Override
  protected @NotNull JComponent createCenterPanel() {
    JPanel optionsPanel = new JPanel();
    optionsPanel.setBorder(JBUI.Borders.empty(10, UIUtil.DEFAULT_HGAP, 0, 0));
    optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));

    myRbInlineAll = new JRadioButton();
    myRbInlineAll.setText(getInlineAllText());
    myRbInlineAll.setSelected(true);
    myRbInlineThisOnly = new JRadioButton();
    myRbInlineThisOnly.setText(getInlineThisText());
    final boolean writable = allowInlineAll();
    optionsPanel.add(myRbInlineAll);
    String keepDeclarationText = getKeepTheDeclarationText();
    if (keepDeclarationText != null && writable) {
      myKeepTheDeclaration = new JRadioButton();
      myKeepTheDeclaration.setText(keepDeclarationText);
      optionsPanel.add(myKeepTheDeclaration);
    }

    optionsPanel.add(myRbInlineThisOnly);
    ButtonGroup bg = new ButtonGroup();
    final JRadioButton[] buttons = myKeepTheDeclaration != null
                                   ? new JRadioButton[] {myRbInlineAll, myKeepTheDeclaration, myRbInlineThisOnly}
                                   : new JRadioButton[] {myRbInlineAll, myRbInlineThisOnly};
    for (JRadioButton button : buttons) {
      bg.add(button);
    }
    RadioUpDownListener.installOn(buttons);

    myRbInlineThisOnly.setEnabled(myInvokedOnReference);
    myRbInlineAll.setEnabled(writable);
    if (myInvokedOnReference) {
      if (canInlineThisOnly()) {
        myRbInlineAll.setSelected(false);
        myRbInlineAll.setEnabled(false);

        if (myKeepTheDeclaration != null) {
          myKeepTheDeclaration.setSelected(false);
          myKeepTheDeclaration.setEnabled(false);
        }

        myRbInlineThisOnly.setSelected(true);
      } else {
        if (writable) {
          final boolean inlineThis = isInlineThis();
          myRbInlineThisOnly.setSelected(inlineThis);
          if (myKeepTheDeclaration != null) myKeepTheDeclaration.setSelected(!inlineThis && isKeepTheDeclarationByDefault());
          myRbInlineAll.setSelected(!inlineThis && !isKeepTheDeclarationByDefault());
        }
        else {
          myRbInlineAll.setSelected(false);
          if (myKeepTheDeclaration != null) {
            myKeepTheDeclaration.setSelected(false);
          }
          myRbInlineThisOnly.setSelected(true);
        }
      }
    }
    else {
      boolean keepTheDeclarationByDefault = isKeepTheDeclarationByDefault();
      myRbInlineAll.setSelected(!keepTheDeclarationByDefault);
      if (myKeepTheDeclaration != null) {
        myKeepTheDeclaration.setSelected(keepTheDeclarationByDefault);
      }
      myRbInlineThisOnly.setSelected(false);
    }

    getPreviewAction().setEnabled(!isInlineThisOnly());
    final ActionListener previewListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        getPreviewAction().setEnabled(!isInlineThisOnly());
      }
    };
    for (JRadioButton button : buttons) {
      button.addActionListener(previewListener);
    }


    return optionsPanel;
  }

  protected boolean allowInlineAll() {
    return myElement.isWritable();
  }

  protected abstract @Label String getNameLabelText();
  
  /** @deprecated Unused since 2011 */
  @Deprecated protected @BorderTitle String getBorderTitle() { return null; }
  protected abstract @RadioButton String getInlineAllText();
  protected @RadioButton String getKeepTheDeclarationText() {return null;}
  protected boolean isKeepTheDeclarationByDefault() {
    return false;
  }
  protected abstract @RadioButton String getInlineThisText();
  protected abstract boolean isInlineThis();
  protected boolean canInlineThisOnly() {
    return false;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myRbInlineThisOnly.isSelected() ? myRbInlineThisOnly : myRbInlineAll;
  }


  protected boolean ignoreOccurrence(PsiReference reference) {
    return false;
  }

  protected static int initOccurrencesNumber(PsiNameIdentifierOwner nameIdentifierOwner) {
    return getNumberOfOccurrences(nameIdentifierOwner, reference -> true);
  }

  protected int getNumberOfOccurrences(PsiNameIdentifierOwner nameIdentifierOwner) {
    return getNumberOfOccurrences(nameIdentifierOwner, this::ignoreOccurrence);
  }

  protected static int getNumberOfOccurrences(PsiNameIdentifierOwner nameIdentifierOwner,
                                              Predicate<? super PsiReference> ignoreOccurrence) {
    return getNumberOfOccurrences(nameIdentifierOwner, ignoreOccurrence, scope -> ReferencesSearch.search(nameIdentifierOwner, scope));
  }

  protected static int getNumberOfOccurrences(PsiNameIdentifierOwner nameIdentifierOwner,
                                              Predicate<? super PsiReference> ignoreOccurrence,
                                              Function<? super GlobalSearchScope, ? extends Query<PsiReference>> searcher) {
    final PsiSearchHelper searchHelper = PsiSearchHelper.getInstance(nameIdentifierOwner.getProject());
    final GlobalSearchScope scope = GlobalSearchScope.projectScope(nameIdentifierOwner.getProject());
    final String name = nameIdentifierOwner.getName();
    final boolean isCheapToSearch =
     name != null && searchHelper.isCheapEnoughToSearch(name, scope, null) != PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES;
    return isCheapToSearch ? (int)searcher.apply(scope).findAll().stream().filter(ignoreOccurrence).count() : - 1;
  }

}
