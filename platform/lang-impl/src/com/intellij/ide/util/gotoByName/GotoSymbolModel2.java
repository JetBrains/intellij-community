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
package com.intellij.ide.util.gotoByName;

import com.intellij.ide.IdeBundle;
import com.intellij.navigation.ChooseByNameRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.PsiElement;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import org.jetbrains.annotations.NotNull;

public class GotoSymbolModel2 extends ContributorsBasedGotoByModel {
  public GotoSymbolModel2(Project project) {
    super(project, ChooseByNameRegistry.getInstance().getSymbolModelContributors());
  }

  public String getPromptText() {
    return IdeBundle.message("prompt.gotosymbol.enter.symbol.name");
  }

  public String getCheckBoxName() {
    return IdeBundle.message("checkbox.include.non.project.symbols");
  }

  public String getNotInMessage() {
    return IdeBundle.message("label.no.matches.found.in.project");
  }

  public String getNotFoundMessage() {
    return IdeBundle.message("label.no.matches.found");
  }

  public char getCheckBoxMnemonic() {
    // Some combination like Alt+N, Ant+O, etc are a dead sysmbols, therefore
    // we have to change mnemonics for Mac users.
    return SystemInfo.isMac?'P':'n';
  }

  public boolean loadInitialCheckBoxState() {
    return false;
  }

  public void saveInitialCheckBoxState(boolean state) {
  }

  public String getFullName(final Object element) {
    if (element instanceof PsiElement) {
      final PsiElement psiElement = (PsiElement)element;

      final String containerText = SymbolPresentationUtil.getSymbolContainerText(psiElement);
      return containerText + "." + getElementName(element);
    }

    return getElementName(element);
  }

  @NotNull
  public String[] getSeparators() {
    return new String[] {"."};
  }

  @Override
  public String getHelpId() {
    return "procedures.navigating.goto.class";
  }
}
