// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.util;

import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;
import org.jetbrains.annotations.NotNull;


public class RefactoringDescriptionLocation extends ElementDescriptionLocation {
  private final boolean myWithParent;

  protected RefactoringDescriptionLocation(boolean withParent) {
    myWithParent = withParent;
  }

  public static final RefactoringDescriptionLocation WITH_PARENT = new RefactoringDescriptionLocation(true);
  public static final RefactoringDescriptionLocation WITHOUT_PARENT = new RefactoringDescriptionLocation(false);

  public boolean includeParent() {
    return myWithParent;
  }

  @Override
  public @NotNull ElementDescriptionProvider getDefaultProvider() {
    return DefaultRefactoringElementDescriptionProvider.INSTANCE;
  }
}
