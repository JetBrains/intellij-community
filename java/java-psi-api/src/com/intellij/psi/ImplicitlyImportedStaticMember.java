// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Class representing an implicitly imported static member.
 * If memberName is `*`, it is on demand import
 */
@ApiStatus.Experimental
public final class ImplicitlyImportedStaticMember implements ImplicitlyImportedElement {

  private final @NotNull String myContainingClass;
  private final @NotNull String myMemberName;
  private final @NotNull PsiImportStaticStatement myImportStaticStatement;

  private ImplicitlyImportedStaticMember(@NotNull Project project, @NotNull String containingClass, @NotNull String memberName) {
    myContainingClass = containingClass;
    myMemberName = memberName;
    myImportStaticStatement = PsiElementFactory.getInstance(project).createImportStaticStatementFromText(getContainingClass(), getMemberName());
  }

  public @NotNull String getContainingClass() {
    return myContainingClass;
  }

  public @NotNull String getMemberName() {
    return myMemberName;
  }

  public boolean isOnDemand() {
    return "*".equals(myMemberName);
  }

  @Override
  public @NotNull PsiImportStatementBase createImportStatement() {
    return myImportStaticStatement;
  }

  public static @NotNull ImplicitlyImportedStaticMember create(@NotNull Project project,
                                                               @NotNull String containingClass,
                                                               @NotNull String memberName) {
    return new ImplicitlyImportedStaticMember(project, containingClass, memberName);
  }
}
