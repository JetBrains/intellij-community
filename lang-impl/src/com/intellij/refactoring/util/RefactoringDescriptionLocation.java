package com.intellij.refactoring.util;

import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;

/**
 * @author yole
 */
public class RefactoringDescriptionLocation implements ElementDescriptionLocation {
  private boolean myWithParent;

  private RefactoringDescriptionLocation(boolean withParent) {
    myWithParent = withParent;
  }

  public static final RefactoringDescriptionLocation WITH_PARENT = new RefactoringDescriptionLocation(true);
  public static final RefactoringDescriptionLocation WITHOUT_PARENT = new RefactoringDescriptionLocation(false);

  public boolean includeParent() {
    return myWithParent;
  }

  public ElementDescriptionProvider getDefaultProvider() {
    return DefaultRefactoringElementDescriptionProvider.INSTANCE;
  }
}
