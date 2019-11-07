// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public abstract class LocalQuickFixBase implements LocalQuickFix {
  private final String myName;
  private final String myFamilyName;

  /**
   * @param name the name of the quick fix
   */
  protected LocalQuickFixBase(@Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String name) {
    this(name, name);
  }

  /**
   * @param name       the name of the quick fix
   * @param familyName text to appear in "Apply Fix" popup when multiple Quick Fixes exist (in the results of batch code inspection). For example,
   *                   if the name of the quickfix is "Create template &lt;filename&gt", the return value of getFamilyName() should be "Create template".
   *                   If the name of the quickfix does not depend on a specific element, simply return getName().
   */
  protected LocalQuickFixBase(@Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String name,
                              @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String familyName) {
    myName = name;
    myFamilyName = familyName;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return myFamilyName;
  }

  @Override
  public abstract void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor);
}
