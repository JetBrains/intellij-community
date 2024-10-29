// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;


public final class ChooseByNameLanguageFilter extends ChooseByNameFilter<LanguageRef> {
  public ChooseByNameLanguageFilter(@NotNull ChooseByNamePopup popup,
                                    @NotNull FilteringGotoByModel<LanguageRef> languageFilteringGotoByModel,
                                    @NotNull ChooseByNameFilterConfiguration<LanguageRef> languageChooseByNameFilterConfiguration,
                                    @NotNull Project project) {
    super(popup, languageFilteringGotoByModel, languageChooseByNameFilterConfiguration, project);
  }

  @Override
  protected String textForFilterValue(@NotNull LanguageRef value) {
    return value.getDisplayName();
  }

  @Override
  protected @Nullable Icon iconForFilterValue(@NotNull LanguageRef value) {
    return value.getIcon();
  }

  @Override
  protected @NotNull Collection<LanguageRef> getAllFilterValues() {
    return LanguageRef.forAllLanguages();
  }
}
