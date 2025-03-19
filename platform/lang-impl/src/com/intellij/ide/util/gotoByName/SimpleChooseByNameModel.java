// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public abstract class SimpleChooseByNameModel implements ChooseByNameModel {
  private final Project myProject;
  private final @Nls(capitalization = Nls.Capitalization.Sentence) String myPrompt;
  private final @NonNls String myHelpId;

  protected SimpleChooseByNameModel(@NotNull Project project,
                                    @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String prompt,
                                    @Nullable @NonNls String helpId) {
    myProject = project;
    myPrompt = prompt;
    myHelpId = helpId;
  }

  public abstract String[] getNames();

  protected abstract Object[] getElementsByName(String name, String pattern);


  public Project getProject() {
    return myProject;
  }

  @Override
  public String getPromptText() {
    return myPrompt;
  }

  @Override
  public @NotNull String getNotInMessage() {
    return InspectionsBundle.message("nothing.found");
  }

  @Override
  public @NotNull String getNotFoundMessage() {
    return InspectionsBundle.message("nothing.found");
  }

  @Override
  public String getCheckBoxName() {
    return null;
  }


  @Override
  public boolean loadInitialCheckBoxState() {
    return false;
  }

  @Override
  public void saveInitialCheckBoxState(boolean state) {
  }

  @Override
  public String @NotNull [] getNames(boolean checkBoxState) {
    return getNames();
  }

  @Override
  public Object @NotNull [] getElementsByName(@NotNull String name, boolean checkBoxState, @NotNull String pattern) {
    return getElementsByName(name, pattern);
  }

  @Override
  public String @NotNull [] getSeparators() {
    return ArrayUtilRt.EMPTY_STRING_ARRAY;
  }

  @Override
  public String getFullName(@NotNull Object element) {
    return getElementName(element);
  }

  @Override
  public String getHelpId() {
    return myHelpId;
  }

  @Override
  public boolean willOpenEditor() {
    return false;
  }

  @Override
  public boolean useMiddleMatching() {
    return false;
  }
}
