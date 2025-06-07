// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm;

import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.Nullable;

public enum JvmModifier {
  PUBLIC,
  PROTECTED,
  PRIVATE,
  PACKAGE_LOCAL,

  STATIC,
  ABSTRACT,
  FINAL,

  NATIVE,
  SYNCHRONIZED,
  STRICTFP,
  TRANSIENT,
  VOLATILE,
  TRANSITIVE;

  /**
   * @param modifier modifier declared in {@link PsiModifier} to convert from
   * @return the corresponding {@code JvmModifier}; null if there's no corresponding {@code JvmModifier}
   */
  public static @Nullable JvmModifier fromPsiModifier(@PsiModifier.ModifierConstant String modifier) {
    switch (modifier) {
      case PsiModifier.PUBLIC:
        return PUBLIC;
      case PsiModifier.PROTECTED:
        return PROTECTED;
      case PsiModifier.PRIVATE:
        return PRIVATE;
      case PsiModifier.PACKAGE_LOCAL:
        return PACKAGE_LOCAL;
      case PsiModifier.STATIC:
        return STATIC;
      case PsiModifier.ABSTRACT:
        return ABSTRACT;
      case PsiModifier.FINAL:
        return FINAL;
      case PsiModifier.NATIVE:
        return NATIVE;
      case PsiModifier.SYNCHRONIZED:
        return SYNCHRONIZED;
      case PsiModifier.STRICTFP:
        return STRICTFP;
      case PsiModifier.TRANSIENT:
        return TRANSIENT;
      case PsiModifier.VOLATILE:
        return VOLATILE;
      case PsiModifier.TRANSITIVE:
        return TRANSITIVE;
      default:
        return null;
    }
  }
}
