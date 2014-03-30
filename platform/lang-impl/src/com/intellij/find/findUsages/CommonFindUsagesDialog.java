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

package com.intellij.find.findUsages;

import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usageView.UsageViewUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author max
 */
public class CommonFindUsagesDialog extends AbstractFindUsagesDialog {
  @NotNull protected final PsiElement myPsiElement;

  public CommonFindUsagesDialog(@NotNull PsiElement element,
                                @NotNull Project project,
                                @NotNull FindUsagesOptions findUsagesOptions,
                                boolean toShowInNewTab,
                                boolean mustOpenInNewTab,
                                boolean isSingleFile,
                                FindUsagesHandler handler) {
    super(project, findUsagesOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile, isTextSearch(element, isSingleFile, handler),
          !isSingleFile && !element.getManager().isInProject(element));
    myPsiElement = element;
    init();
  }

  private static boolean isTextSearch(PsiElement element, boolean isSingleFile, FindUsagesHandler handler) {
    return FindUsagesUtil.isSearchForTextOccurrencesAvailable(element, isSingleFile, handler);
  }

  @Override
  protected boolean isInFileOnly() {
    return super.isInFileOnly() ||
           PsiSearchHelper.SERVICE.getInstance(myPsiElement.getProject()).getUseScope(myPsiElement) instanceof LocalSearchScope;
  }

  @Override
  protected JPanel createFindWhatPanel() {
    return null;
  }

  @Override
  public void configureLabelComponent(@NotNull SimpleColoredComponent coloredComponent) {
    coloredComponent.append(StringUtil.capitalize(UsageViewUtil.getType(myPsiElement)));
    coloredComponent.append(" ");
    coloredComponent.append(DescriptiveNameUtil.getDescriptiveName(myPsiElement), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
  }

  @Override
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(FindUsagesManager.getHelpID(myPsiElement));
  }
}
