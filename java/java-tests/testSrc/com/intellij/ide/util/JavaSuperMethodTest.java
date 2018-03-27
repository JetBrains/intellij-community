/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ide.util;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.FindSuperElementsHelper;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

public class JavaSuperMethodTest extends LightDaemonAnalyzerTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  private static String getBasePath() {
    return "/codeInsight/gotosuper/";
  }

  public void testDoNotGoToSiblingInheritanceIfInLibrary() {
    configureByFile(getBasePath() + "OverridingLibrary.java");

    PsiClass aThread = getJavaFacade().findClass("java.lang.Thread");
    PsiMethod startMethod = aThread.findMethodsByName("start", false)[0];
    PsiMethod sibling = FindSuperElementsHelper.getSiblingInheritedViaSubClass(startMethod);
    assertNotNull(sibling);

    Collection<PsiMethod> superMethods = SuperMethodWarningUtil.getSuperMethods(startMethod, aThread, Collections.emptyList());
    assertEmpty(superMethods);
  }
}
