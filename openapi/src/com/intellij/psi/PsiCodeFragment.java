/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi;

import org.jetbrains.annotations.NonNls;

public interface PsiCodeFragment extends PsiFile, PsiImportHolder {
  void setEverythingAcessible(boolean value);

  PsiType getThisType();
  void setThisType(PsiType psiType);

  String importsToString();
  void addImportsFromString(String imports);

  void setVisibilityChecker(VisibilityChecker checker);
  VisibilityChecker getVisibilityChecker();

  void setExceptionHandler(ExceptionHandler checker);
  ExceptionHandler getExceptionHandler();

  PsiType getSuperType();
  void setSuperType(PsiType superType);

  /**
   * @fabrique: override it as appropriate to you
   * */
  boolean isPhysicalChangesProvider();

  interface VisibilityChecker {
    Visibility isDeclarationVisible(PsiElement declaration, PsiElement place);

    public class Visibility {
      public static final Visibility VISIBLE = new Visibility("VISIBLE");
      public static final Visibility NOT_VISIBLE = new Visibility("NOT_VISIBLE");
      public static final Visibility DEFAULT_VISIBILITY = new Visibility("DEFAULT_VISIBILITY");

      private final String myName; // for debug only

      private Visibility(@NonNls String name) {
        myName = name;
      }

      public String toString() {
        return myName;
      }
    }
  }

  interface ExceptionHandler {
    boolean isHandledException(PsiClassType exceptionType);
  }
}
