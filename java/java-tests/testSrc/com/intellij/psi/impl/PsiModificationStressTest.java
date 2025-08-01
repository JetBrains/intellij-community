// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;

public class PsiModificationStressTest extends LightJavaCodeInsightTestCase {
  public void testManySmallPSIChangesDoNotCauseQuadraticRecomputationsOfWholeFileText() {
    int N = 100_000;
    String text = "  int field;\n".repeat(N);

    PsiClass aClass = PsiElementFactory.getInstance(getProject()).createClassFromText(text, null);
    assertFalse(aClass.isPhysical());
    PsiField firstField = aClass.getFields()[0];
    // would pass only if each PSI change does not cause recomputation of the whole file text in com.intellij.psi.impl.source.PsiJavaFileBaseImpl.getLanguageLevelInner
    for (int i = 0; i < N; i++) {
      firstField.setName("f" + i);
    }
    assertFalse(aClass.isPhysical());
    assertEquals(N, aClass.getFields().length);
  }
}
