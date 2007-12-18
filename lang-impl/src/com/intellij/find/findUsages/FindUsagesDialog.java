package com.intellij.find.findUsages;

import com.intellij.find.FindBundle;
import com.intellij.find.FindSettings;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.ui.StateRestoringCheckBox;
import com.intellij.usageView.UsageViewUtil;

import javax.swing.*;

public abstract class FindUsagesDialog extends AbstractFindUsagesDialog {
  protected final PsiElement myPsiElement;
  private StateRestoringCheckBox myCbIncludeOverloadedMethods;
  private boolean myIncludeOverloadedMethodsAvailable = false;

  protected FindUsagesDialog(PsiElement element,
                             Project project,
                             FindUsagesOptions findUsagesOptions,
                             boolean toShowInNewTab,
                             boolean mustOpenInNewTab,
                             boolean isSingleFile) {
    super(project, findUsagesOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile,
          FindUsagesUtil.isSearchForTextOccurencesAvailable(element, isSingleFile), !isSingleFile && !element.getManager().isInProject(element));
    myPsiElement = element;
    myIncludeOverloadedMethodsAvailable = element instanceof PsiMethod && MethodSignatureUtil.hasOverloads((PsiMethod)element);
    init();
  }

  public void calcFindUsagesOptions(FindUsagesOptions options) {
    super.calcFindUsagesOptions(options);
    options.isIncludeOverloadUsages =
      myIncludeOverloadedMethodsAvailable && isToChange(myCbIncludeOverloadedMethods) && myCbIncludeOverloadedMethods.isSelected();
  }

  protected void doOKAction() {
    if (shouldDoOkAction()) {
      if (myIncludeOverloadedMethodsAvailable) {
        FindSettings.getInstance().setSearchOverloadedMethods(myCbIncludeOverloadedMethods.isSelected());
      }
    } else {
      return;
    }
    super.doOKAction();
  }

  protected void addUsagesOptions(JPanel optionsPanel) {
    super.addUsagesOptions(optionsPanel);
    if (myIncludeOverloadedMethodsAvailable) {
      myCbIncludeOverloadedMethods = addCheckboxToPanel(FindBundle.message("find.options.include.overloaded.methods.checkbox"),
                                                        FindSettings.getInstance().isSearchOverloadedMethods(), optionsPanel, false);

    }
  }

  private PsiManager getPsiManager() {
    return myPsiElement.getManager();
  }

  protected boolean isInFileOnly() {
    return super.isInFileOnly() ||
           myPsiElement != null && getPsiManager().getSearchHelper().getUseScope(myPsiElement)instanceof LocalSearchScope;
  }

  public String getLabelText() {
    return StringUtil.capitalize(UsageViewUtil.getType(myPsiElement)) + " " + UsageViewUtil.getDescriptiveName(myPsiElement);
  }

  protected final PsiElement getPsiElement() {
    return myPsiElement;
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(FindUsagesManager.getHelpID(myPsiElement));
  }
}
