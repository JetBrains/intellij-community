/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.lang.java;

import com.intellij.lang.Language;
import com.intellij.psi.ElementDescriptionUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.refactoring.util.RefactoringDescriptionLocation;
import com.intellij.usageView.UsageViewShortNameLocation;
import com.intellij.xml.breadcrumbs.BreadcrumbsInfoProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author gregsh
 */
public class JavaBreadcrumbsInfoProvider extends BreadcrumbsInfoProvider {
  private static final Language[] ourLanguages = {JavaLanguage.INSTANCE};
  @Override
  public Language[] getLanguages() {
    return ourLanguages;
  }

  @Override
  public boolean acceptElement(@NotNull PsiElement e) {
    return e instanceof PsiMember;
  }

  @NotNull
  @Override
  public String getElementInfo(@NotNull PsiElement e) {
    return ElementDescriptionUtil.getElementDescription(e, UsageViewShortNameLocation.INSTANCE);
  }

  @Nullable
  @Override
  public String getElementTooltip(@NotNull PsiElement e) {
    return ElementDescriptionUtil.getElementDescription(e, RefactoringDescriptionLocation.WITH_PARENT);
  }
}
