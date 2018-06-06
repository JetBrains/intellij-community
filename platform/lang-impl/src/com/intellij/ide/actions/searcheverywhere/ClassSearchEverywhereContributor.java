// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.gotoByName.FilteringGotoByModel;
import com.intellij.ide.util.gotoByName.GotoClassModel2;
import com.intellij.lang.DependentLanguage;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.ui.IdeUICustomization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.function.Function;
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
