// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Contributes to the data flow analysis the nullability of a value that the analyzed code does not produce itself.
 * A framework, a container or a code generator can supply such a value at run time:
 * <ul>
 *   <li>a parameter value that the method body can rely on at the method start;</li>
 *   <li>a value that a call of the method returns;</li>
 *   <li>a value that something outside the code writes into a field, for example an injection.</li>
 * </ul>
 * <p>
 * The answer describes the values that the framework supplies at run time. Do not report a convention or a guess.
 * A direct call of the method with another value breaks the contract, as a null argument breaks a not-null parameter.
 * The answer is not a declaration. The data flow analysis uses it only when it reads the value,
 * so the argument checks, the assignment checks and the nullability inspections do not see it.
 * For a field, the data flow analysis uses a not-null answer only when the field is private,
 * has no initializer and the code never writes the field. A nullable answer for a field applies to each read of the field.
 */
@ApiStatus.Experimental
public interface DataflowNullabilityContributor {
  ExtensionPointName<DataflowNullabilityContributor> EP_NAME = ExtensionPointName.create("com.intellij.codeInsight.dataflowNullabilityContributor");

  /**
   * @return the nullability of a parameter value at the method start, of a method result,
   * or of a value that something outside the code writes into a field, or null if the contributor does not know the value
   */
  @Nullable Nullability getNullability(@NotNull PsiModifierListOwner owner);

  static @Nullable Nullability findNullability(@NotNull PsiModifierListOwner owner) {
    if (DumbService.isDumb(owner.getProject())) return null;
    return EP_NAME.computeSafeIfAny(contributor -> contributor.getNullability(owner));
  }
}
