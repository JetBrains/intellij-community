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
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.Language;
import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.ChooseByNameRegistry;
import com.intellij.navigation.GotoClassContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.PsiElement;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;

public class GotoSymbolModel2 extends FilteringGotoByModel<Language> {
  private String[] mySeparators;
  
  public GotoSymbolModel2(@NotNull Project project) {
    super(project, ChooseByNameRegistry.getInstance().getSymbolModelContributors());
  }

  @Override
  protected Language filterValueFor(NavigationItem item) {
    return item instanceof PsiElement ? ((PsiElement) item).getLanguage() : null;
  }

  @Nullable
  @Override
  protected synchronized Collection<Language> getFilterItems() {
    final Collection<Language> result = super.getFilterItems();
    if (result == null) {
      return result;
    }
    final Collection<Language> items = new HashSet<>(result);
    items.add(Language.ANY);
    return items;
  }

  @Override
  public String getPromptText() {
    return IdeBundle.message("prompt.gotosymbol.enter.symbol.name");
  }

  @Override
  public String getCheckBoxName() {
    return IdeBundle.message("checkbox.include.non.project.symbols");
  }

  @Override
  public String getNotInMessage() {
    return IdeBundle.message("label.no.matches.found.in.project");
  }

  @Override
  public String getNotFoundMessage() {
    return IdeBundle.message("label.no.matches.found");
  }

  @Override
  public char getCheckBoxMnemonic() {
    // Some combination like Alt+N, Ant+O, etc are a dead symbols, therefore
    // we have to change mnemonics for Mac users.
    return SystemInfo.isMac?'P':'n';
  }

  @Override
  public boolean loadInitialCheckBoxState() {
    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(myProject);
    return Boolean.TRUE.toString().equals(propertiesComponent.getValue("GoToClass.toSaveIncludeLibraries")) &&
           Boolean.TRUE.toString().equals(propertiesComponent.getValue("GoToSymbol.includeLibraries"));
  }

  @Override
  public void saveInitialCheckBoxState(boolean state) {
    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(myProject);
    if (Boolean.TRUE.toString().equals(propertiesComponent.getValue("GoToClass.toSaveIncludeLibraries"))){
      propertiesComponent.setValue("GoToSymbol.includeLibraries", Boolean.toString(state));
    }
  }

  @Override
  public String getFullName(final Object element) {
    for(ChooseByNameContributor c: getContributors()) {
      if (c instanceof GotoClassContributor) {
        String result = ((GotoClassContributor) c).getQualifiedName((NavigationItem) element);
        if (result != null) {
          return result;
        }
      }
    }

    String elementName = getElementName(element);
    if (elementName == null) return null;
    
    if (element instanceof PsiElement) {
      return SymbolPresentationUtil.getSymbolContainerText((PsiElement)element) + "." + elementName;
    }

    return elementName;
  }

  @Override
  @NotNull
  public String[] getSeparators() {
    if (mySeparators == null) {
      mySeparators = GotoClassModel2.getSeparatorsFromContributors(getContributors());
    }
    return mySeparators;
  }

  @Override
  public String getHelpId() {
    return "procedures.navigating.goto.class";
  }

  @Override
  public boolean willOpenEditor() {
    return true;
  }
}
