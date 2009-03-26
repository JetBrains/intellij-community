package com.intellij.find.findUsages;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.help.HelpManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.LocalSearchScope;
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
  private final PsiElement myPsiElement;

  public CommonFindUsagesDialog(PsiElement element,
                                Project project,
                                FindUsagesOptions findUsagesOptions,
                                boolean toShowInNewTab,
                                boolean mustOpenInNewTab,
                                boolean isSingleFile, FindUsagesHandler handler) {
    super(project, findUsagesOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile, isTextSearch(element, isSingleFile, handler), !isSingleFile && !element.getManager().isInProject(element));
    myPsiElement = element;
    init();
  }

  private static boolean isTextSearch(PsiElement element, boolean isSingleFile, FindUsagesHandler handler) {
    return FindUsagesUtil.isSearchForTextOccurencesAvailable(element, isSingleFile, handler);
  }

  @Override
  protected boolean isInFileOnly() {
    return super.isInFileOnly() ||
           myPsiElement != null && myPsiElement.getManager().getSearchHelper().getUseScope(myPsiElement)instanceof LocalSearchScope;
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

  @Override
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(FindUsagesManager.getHelpID(myPsiElement));
  }
}
