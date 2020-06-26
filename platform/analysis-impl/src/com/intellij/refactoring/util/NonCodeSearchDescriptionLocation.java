// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.refactoring.util;

import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public final class NonCodeSearchDescriptionLocation extends ElementDescriptionLocation {
  private final boolean myNonJava;

  private NonCodeSearchDescriptionLocation(final boolean nonJava) {
    myNonJava = nonJava;
  }

  public static final NonCodeSearchDescriptionLocation NON_JAVA = new NonCodeSearchDescriptionLocation(true);
  public static final NonCodeSearchDescriptionLocation STRINGS_AND_COMMENTS = new NonCodeSearchDescriptionLocation(false);

  @NotNull
  @Override
  public ElementDescriptionProvider getDefaultProvider() {
    return DefaultNonCodeSearchElementDescriptionProvider.INSTANCE;
  }

  public boolean isNonJava() {
    return myNonJava;
  }
}
