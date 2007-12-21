package com.intellij.find.findUsages;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageViewUtil;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 14, 2005
 * Time: 5:40:05 PM
 * To change this template use File | Settings | File Templates.
 */
public class CommonFindUsagesDialog extends AbstractFindUsagesDialog {
  private PsiElement myPsiElement;

  public CommonFindUsagesDialog(PsiElement element,
                                Project project,
                                FindUsagesOptions findUsagesOptions,
                                boolean toShowInNewTab,
                                boolean mustOpenInNewTab,
                                boolean isSingleFile) {
    super(project, findUsagesOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile,
          FindUsagesUtil.isSearchForTextOccurencesAvailable(element, isSingleFile), !isSingleFile && !element.getManager().isInProject(element));
    myPsiElement = element;
  }

  protected JPanel createFindWhatPanel() {
    return null;
  }

  protected JComponent getPreferredFocusedControl() {
    return null;
  }

  public String getLabelText() {
    return StringUtil.capitalize(UsageViewUtil.getType(myPsiElement)) + " " + UsageViewUtil.getDescriptiveName(myPsiElement);
  }
}
