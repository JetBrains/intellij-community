// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Class representing an implicitly imported static member.
 * If memberName is `*`, it is on demand import
 */
@ApiStatus.Experimental
public final class ImplicitlyImportedStaticMember {
  public static final @NotNull ImplicitlyImportedStaticMember @NotNull [] EMPTY_ARRAY = new ImplicitlyImportedStaticMember[0];

  private final @NotNull String myContainingClass;
  private final @NotNull String myMemberName;

  private ImplicitlyImportedStaticMember(@NotNull String containingClass, @NotNull String memberName) {
    myContainingClass = containingClass;
    myMemberName = memberName;
  }

  @NotNull
  public String getContainingClass() {
    return myContainingClass;
  }

  @NotNull
  public String getMemberName() {
    return myMemberName;
  }

  public boolean isOnDemand() {
    return "*".equals(myMemberName);
  }

  @NotNull
  public static ImplicitlyImportedStaticMember create(@NotNull String containingClass, @NotNull String memberName) {
    return new ImplicitlyImportedStaticMember(containingClass, memberName);
  }
}
