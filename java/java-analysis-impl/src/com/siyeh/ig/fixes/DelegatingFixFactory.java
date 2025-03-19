// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public final class DelegatingFixFactory {

  public static @Nullable LocalQuickFix createMakeSerializableFix(PsiClass aClass) {
    if (aClass instanceof PsiAnonymousClass) {
      return null;
    }
    final PsiClassType type = TypeUtils.getType(CommonClassNames.JAVA_IO_SERIALIZABLE, aClass);
    return QuickFixFactory.getInstance().createExtendsListFix(aClass, type, true);
  }

  public static @Nullable LocalQuickFix createMakeCloneableFix(PsiClass aClass) {
    if (aClass instanceof PsiAnonymousClass) {
      return null;
    }
    final PsiClassType type = TypeUtils.getType(CommonClassNames.JAVA_LANG_CLONEABLE, aClass);
    return QuickFixFactory.getInstance().createExtendsListFix(aClass, type, true);
  }
}