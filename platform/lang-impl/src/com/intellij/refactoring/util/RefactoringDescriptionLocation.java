// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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

  @NotNull
  @Override
  public ElementDescriptionProvider getDefaultProvider() {
    return DefaultRefactoringElementDescriptionProvider.INSTANCE;
  }
}
