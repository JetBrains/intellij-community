package com.intellij.refactoring.util;

import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;

/**
 * @author yole
 */
public class NonCodeSearchDescriptionLocation implements ElementDescriptionLocation {
  private boolean myNonJava;

  private NonCodeSearchDescriptionLocation(final boolean nonJava) {
    myNonJava = nonJava;
  }

  public static final NonCodeSearchDescriptionLocation NON_JAVA = new NonCodeSearchDescriptionLocation(true);
  public static final NonCodeSearchDescriptionLocation STRINGS_AND_COMMENTS = new NonCodeSearchDescriptionLocation(false);

  public ElementDescriptionProvider getDefaultProvider() {
    return DefaultNonCodeSearchElementDescriptionProvider.INSTANCE;
  }

  public boolean isNonJava() {
    return myNonJava;
  }
}
