// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.GotoClassAction;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.ide.util.gotoByName.ChooseByNamePopup;
import com.intellij.ide.util.gotoByName.FilteringGotoByModel;
import com.intellij.ide.util.gotoByName.GotoClassModel2;
import com.intellij.lang.DependentLanguage;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.IdeUICustomization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

/**
 * @author Konstantin Bulenkov
 */
public class ClassSearchEverywhereContributor extends AbstractGotoSEContributor<Language> {

  public ClassSearchEverywhereContributor(Project project) {
    super(project);
  }

  @NotNull
  @Override
  public String getGroupName() {
    return "Classes";
  }

  @Override
  public String includeNonProjectItemsText() {
    return IdeBundle.message("checkbox.include.non.project.classes", IdeUICustomization.getInstance().getProjectConceptName());
  }

  @Override
  public int getSortWeight() {
    return 100;
  }

  @Override
  protected FilteringGotoByModel<Language> createModel(Project project) {
    return new GotoClassModel2(project);
  }

  @Override
  public boolean processSelectedItem(Object selected, int modifiers, String searchText) {
    if (selected instanceof PsiElement && ((PsiElement)selected).isValid()) {
      PsiElement psiElement = (PsiElement) selected;
      String path = pathToAnonymousClass(searchText);
      if (path != null) {
        psiElement = GotoClassAction.getElement(psiElement, path);
      }
      psiElement = psiElement.getNavigationElement();
      VirtualFile file = PsiUtilCore.getVirtualFile(psiElement);

      Pair<Integer, Integer> position = getLineAndColumn(searchText);
      boolean positionSpecified = position.first >= 0 || position.second >= 0;
      if (file != null && positionSpecified) {
        OpenFileDescriptor descriptor = new OpenFileDescriptor(psiElement.getProject(), file, position.first, position.second);
        descriptor = descriptor.setUseCurrentWindow(openInCurrentWindow(modifiers));
        if (descriptor.canNavigate()) {
          descriptor.navigate(true);
          return true;
        }
      }

      String memberName = getMemberName(searchText);
      if (file != null && memberName != null) {
        NavigationUtil.activateFileWithPsiElement(psiElement, openInCurrentWindow(modifiers));
        Navigatable member = GotoClassAction.findMember(memberName, searchText, psiElement, file);
        if (member != null) {
          member.navigate(true);
          return true;
        }
      }

      NavigationUtil.activateFileWithPsiElement(psiElement, openInCurrentWindow(modifiers));
    }
    else {
      EditSourceUtil.navigate(((NavigationItem)selected), true, openInCurrentWindow(modifiers));
    }

    return true;
  }

  private static String pathToAnonymousClass(String searchedText) {
    final Matcher matcher = ChooseByNamePopup.patternToDetectAnonymousClasses.matcher(searchedText);
    if (matcher.matches()) {
      String path = matcher.group(2);
      if (path != null) {
        path = path.trim();
        if (path.endsWith("$") && path.length() >= 2) {
          path = path.substring(0, path.length() - 2);
        }
        if (!path.isEmpty()) return path;
      }
    }

    return null;
  }

  private static String getMemberName(String searchedText) {
    final int index = searchedText.lastIndexOf('#');
    if (index == -1) {
      return null;
    }

    String name = searchedText.substring(index + 1).trim();
    return StringUtil.isEmpty(name) ? null : name;
  }

  public static class Factory implements SearchEverywhereContributorFactory<Language> {
    public static final Function<Language, String> LANGUAGE_NAME_EXTRACTOR = Language::getDisplayName;
    public static final Function<Language, Icon> LANGUAGE_ICON_EXTRACTOR = language -> {
      final LanguageFileType fileType = language.getAssociatedFileType();
      return fileType != null ? fileType.getIcon() : null;
    };

    @NotNull
    @Override
    public SearchEverywhereContributor<Language> createContributor(AnActionEvent initEvent) {
      return new ClassSearchEverywhereContributor(initEvent.getProject());
    }

    @Nullable
    @Override
    public SearchEverywhereContributorFilter<Language> createFilter() {
      List<Language> items = Language.getRegisteredLanguages()
                                     .stream()
                                     .filter(lang -> lang != Language.ANY && !(lang instanceof DependentLanguage))
                                     .sorted(LanguageUtil.LANGUAGE_COMPARATOR)
                                     .collect(Collectors.toList());
      return new SearchEverywhereContributorFilterImpl<>(items, LANGUAGE_NAME_EXTRACTOR, LANGUAGE_ICON_EXTRACTOR);
    }
  }
}
