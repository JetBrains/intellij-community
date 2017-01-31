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
package com.intellij.codeInspection.deprecation;

import com.intellij.codeInspection.DeprecationUtil;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author max
 */
public class DeprecationInspection extends DeprecationInspectionBase {
  public static final String SHORT_NAME = DeprecationUtil.DEPRECATION_SHORT_NAME;
  public static final String ID = DeprecationUtil.DEPRECATION_ID;
  public static final String DISPLAY_NAME = DeprecationUtil.DEPRECATION_DISPLAY_NAME;
  public static final String IGNORE_METHODS_OF_DEPRECATED_NAME = "IGNORE_METHODS_OF_DEPRECATED";

  public boolean IGNORE_INSIDE_DEPRECATED = true;
  public boolean IGNORE_ABSTRACT_DEPRECATED_OVERRIDES = true;
  public boolean IGNORE_IMPORT_STATEMENTS = true;
  public boolean IGNORE_METHODS_OF_DEPRECATED = true;

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new DeprecationElementVisitor(holder, IGNORE_INSIDE_DEPRECATED, IGNORE_ABSTRACT_DEPRECATED_OVERRIDES,
                                         IGNORE_IMPORT_STATEMENTS, IGNORE_METHODS_OF_DEPRECATED,
                                         false);
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return "";
  }

  @Override
  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  @Override
  @NotNull
  @SuppressWarnings("PatternOverriddenByNonAnnotatedMethod")
  public String getID() {
    return ID;
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox("Ignore inside deprecated members", "IGNORE_INSIDE_DEPRECATED");
    panel.addCheckbox("Ignore inside non-static imports", "IGNORE_IMPORT_STATEMENTS");
    panel.addCheckbox("<html>Ignore overrides of deprecated abstract methods from non-deprecated supers</html>", "IGNORE_ABSTRACT_DEPRECATED_OVERRIDES");
    panel.addCheckbox("Ignore members of deprecated classes", IGNORE_METHODS_OF_DEPRECATED_NAME);
    return panel;
  }

  public static void checkDeprecated(PsiElement refElement,
                                     PsiElement elementToHighlight,
                                     @Nullable TextRange rangeInElement,
                                     ProblemsHolder holder) {
    checkDeprecated(refElement, elementToHighlight, rangeInElement, false, false, true, holder, false);
  }
}
