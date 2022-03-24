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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import org.jetbrains.annotations.NotNull;

public class RenameToIgnoredFix extends RenameElementFix {
  private static final String PREFIX = "ignored";

  private RenameToIgnoredFix(@NotNull PsiNamedElement place, @NotNull String suffix) {
    super(place, JavaCodeStyleManager.getInstance(place.getProject()).suggestUniqueVariableName(PREFIX + suffix, place, true));
  }

  /**
   * @param useElementNameAsSuffix if true, let the fix suggest the variable name that consists of the "ignored" and name of the element
   *                               e.g. ignoredVar
   *                               <p>if false, let the fix suggest the variable that consists of the "ignored" and some number
   *                               e.g. ignored1
   */
  public static RenameToIgnoredFix createRenameToIgnoreFix(@NotNull PsiNamedElement element, boolean useElementNameAsSuffix) {
    if (useElementNameAsSuffix) {
      String elementName = element.getName();
      if (elementName != null) return new RenameToIgnoredFix(element, StringUtil.capitalize(elementName));
    }
    return new RenameToIgnoredFix(element, "");
  }
}
