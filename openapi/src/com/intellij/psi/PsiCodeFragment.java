/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

public interface PsiCodeFragment extends PsiFile {
  void setEverythingAcessible(boolean value);

  PsiType getThisType();
  void setThisType(PsiType psiType);

  String importsToString();
  void addImportsFromString(String imports);

  void setVisibilityChecker(VisibilityChecker checker);
  VisibilityChecker getVisibilityChecker();

  interface VisibilityChecker {
    Visibility isDeclarationVisible(PsiElement declaration, PsiElement place);

    public class Visibility {
      public static final Visibility VISIBLE = new Visibility("VISIBLE");
      public static final Visibility NOT_VISIBLE = new Visibility("NOT_VISIBLE");
      public static final Visibility DEFAULT_VISIBILITY = new Visibility("DEFAULT_VISIBILITY");

      private final String myName; // for debug only

      private Visibility(String name) {
        myName = name;
      }

      public String toString() {
        return myName;
      }
    }
  }
}
