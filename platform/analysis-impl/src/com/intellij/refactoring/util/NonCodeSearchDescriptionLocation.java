// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.util;

import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;
import org.jetbrains.annotations.NotNull;


public final class NonCodeSearchDescriptionLocation extends ElementDescriptionLocation {
  private final boolean myNonJava;

  private NonCodeSearchDescriptionLocation(final boolean nonJava) {
    myNonJava = nonJava;
  }

  public static final NonCodeSearchDescriptionLocation NON_JAVA = new NonCodeSearchDescriptionLocation(true);
  public static final NonCodeSearchDescriptionLocation STRINGS_AND_COMMENTS = new NonCodeSearchDescriptionLocation(false);

  @Override
  public @NotNull ElementDescriptionProvider getDefaultProvider() {
    return DefaultNonCodeSearchElementDescriptionProvider.INSTANCE;
  }

  public boolean isNonJava() {
    return myNonJava;
  }
}
