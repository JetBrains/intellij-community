// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.Language;
import com.intellij.navigation.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.ui.IdeUICustomization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class GotoSymbolModel2 extends FilteringGotoByModel<LanguageRef> {
  private String[] mySeparators;
  private final boolean myAllContributors;

  public GotoSymbolModel2(@NotNull Project project,
                          @NotNull List<ChooseByNameContributor> contributors,
                          @NotNull Disposable parentDisposable) {
    super(project, contributors);

    myAllContributors = false;
    addEpListener(parentDisposable);
  }

  /**
   * @deprecated Please pass parent disposable explicitly
   */
  @Deprecated
  public GotoSymbolModel2(@NotNull Project project) {
    this(project, project);
  }

  public GotoSymbolModel2(@NotNull Project project, @NotNull Disposable parentDisposable) {
    super(project, List.of());
    myAllContributors = true;
    addEpListener(parentDisposable);
  }

  private void addEpListener(@NotNull Disposable parentDisposable) {
    ChooseByNameContributor.CLASS_EP_NAME.addChangeListener(() -> mySeparators = null, parentDisposable);
  }

  @Override
  protected List<ChooseByNameContributor> getContributorList() {
    if (myAllContributors) {
      return ChooseByNameRegistry.getInstance().getSymbolModelContributors();
    }
    return super.getContributorList();
  }

  @Override
  protected LanguageRef filterValueFor(NavigationItem item) {
    return LanguageRef.forNavigationitem(item);
  }

  @Override
  protected synchronized @Nullable Collection<LanguageRef> getFilterItems() {
    final Collection<LanguageRef> result = super.getFilterItems();
    if (result == null) {
      return null;
    }
    final Collection<LanguageRef> items = new HashSet<>(result);
    items.add(LanguageRef.forLanguage(Language.ANY));
    return items;
  }

  @Override
  public String getPromptText() {
    return IdeBundle.message("prompt.gotosymbol.enter.symbol.name");
  }

  @Override
  public String getCheckBoxName() {
    return IdeUICustomization.getInstance().projectMessage("checkbox.include.non.project.symbols");
  }

  @Override
  public @NotNull String getNotInMessage() {
    return IdeUICustomization.getInstance().projectMessage("label.no.matches.found.in.project");
  }

  @Override
  public @NotNull String getNotFoundMessage() {
    return IdeBundle.message("label.no.matches.found");
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
    if (Boolean.TRUE.toString().equals(propertiesComponent.getValue("GoToClass.toSaveIncludeLibraries"))) {
      propertiesComponent.setValue("GoToSymbol.includeLibraries", Boolean.toString(state));
    }
  }

  @Override
  public String getFullName(final @NotNull Object element) {
    for (ChooseByNameContributor c : getContributorList()) {
      if (c instanceof GotoClassContributor) {
        String result = ((GotoClassContributor)c).getQualifiedName((NavigationItem)element);
        if (result != null) {
          return result;
        }
      }
    }

    String elementName = getElementName(element);
    if (elementName == null) return null;

    PsiElement psiElement = null;
    if (element instanceof PsiElement psi) {
      psiElement = psi;
    } else if (element instanceof PsiElementNavigationItem item) {
      psiElement = item.getTargetElement();
    }
    if (psiElement != null) {
      return SymbolPresentationUtil.getSymbolContainerText(psiElement) + "." + elementName;
    }

    return elementName;
  }

  @Override
  public String @NotNull [] getSeparators() {
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
