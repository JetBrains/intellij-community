/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;

public class RenameToIgnoredFix extends RenameElementFix {
  private static final String PREFIX = "ignored";

  private RenameToIgnoredFix(@NotNull PsiNamedElement place, @NotNull String name) {
    super(place, name);
  }

  /**
   * @param useElementNameAsSuffix if true, let the fix suggest the variable name that consists of the "ignored" and name of the element
   *                               e.g. ignoredVar
   *                               <p>if false, let the fix suggest the variable that consists of the "ignored" and some number
   *                               e.g. ignored1.
   *                               If variable can be unnamed (place and language level allows, and it's totally unused), renaming to unnamed
   *                               will be suggested instead.
   */
  public static RenameToIgnoredFix createRenameToIgnoreFix(@NotNull PsiNamedElement element, boolean useElementNameAsSuffix) {
    if (element instanceof PsiVariable variable && canBeUnnamed(variable)
        && !VariableAccessUtils.variableIsUsed(variable, PsiUtil.getVariableCodeBlock(variable, null))) {
      return new RenameToIgnoredFix(variable, "_");
    }
    String baseName = "";
    if (useElementNameAsSuffix) {
      String elementName = element.getName();
      if (elementName != null) {
        baseName = StringUtil.capitalize(elementName);
      }
    }
    return new RenameToIgnoredFix(element, JavaCodeStyleManager.getInstance(element.getProject())
      .suggestUniqueVariableName(PREFIX + baseName, element, true));
  }

  private static boolean canBeUnnamed(PsiVariable variable) {
    if (!HighlightingFeature.UNNAMED_PATTERNS_AND_VARIABLES.isAvailable(variable)) return false;
    if (variable instanceof PsiPatternVariable || variable instanceof PsiResourceVariable) return true;
    if (variable instanceof PsiLocalVariable) {
      return variable.getParent() instanceof PsiDeclarationStatement decl && decl.getParent() instanceof PsiCodeBlock;
    }
    if (variable instanceof PsiParameter parameter) {
      return !(parameter.getDeclarationScope() instanceof PsiMethod);
    }
    return false;
  }
}
