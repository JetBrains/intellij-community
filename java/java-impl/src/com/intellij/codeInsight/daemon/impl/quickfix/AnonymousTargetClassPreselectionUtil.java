// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public final class AnonymousTargetClassPreselectionUtil {
  private static final String PRESELECT_ANONYMOUS = "create.member.preselect.anonymous";

  public static void rememberSelection(PsiClass aClass, PsiClass firstClass) {
    if (firstClass instanceof PsiAnonymousClass) {
      PropertiesComponent.getInstance().setValue(PRESELECT_ANONYMOUS, aClass == firstClass);
    }
  }

  public static @Nullable PsiClass getPreselection(Collection<? extends PsiClass> classes, PsiClass firstClass) {
    if (firstClass instanceof PsiAnonymousClass && !PropertiesComponent.getInstance().getBoolean(PRESELECT_ANONYMOUS, true)) {
      for (PsiClass aClass : classes) {
        if (!(aClass instanceof PsiAnonymousClass)) {
          return aClass;
        }
      }
    }
    return null;
  }
}
