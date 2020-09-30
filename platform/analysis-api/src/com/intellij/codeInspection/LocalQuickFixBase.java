// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 * @deprecated using {@link LocalQuickFixBase} is error-prone regarding i18n as
 * calculated UI-visible string can be stored somewhere and leaked and it will never updated on language change.
 */
@Deprecated
public abstract class LocalQuickFixBase implements LocalQuickFix {
  private final @IntentionName String myName;
  private final @IntentionFamilyName String myFamilyName;

  /**
   * @param name the name of the quick fix
   */
  protected LocalQuickFixBase(@IntentionName @NotNull String name) {
    this(name, name);
  }

  /**
   * @param name       the name of the quick fix
   * @param familyName text to appear in "Apply Fix" popup when multiple Quick Fixes exist (in the results of batch code inspection). For example,
   *                   if the name of the quickfix is "Create template &lt;filename&gt", the return value of getFamilyName() should be "Create template".
   *                   If the name of the quickfix does not depend on a specific element, simply return getName().
   */
  protected LocalQuickFixBase(@IntentionName @NotNull String name, @IntentionFamilyName @NotNull String familyName) {
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
