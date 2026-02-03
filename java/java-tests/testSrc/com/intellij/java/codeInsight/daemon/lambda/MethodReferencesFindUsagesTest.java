// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.lambda;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import org.jetbrains.annotations.NonNls;

import java.util.Collection;

public class MethodReferencesFindUsagesTest extends LightDaemonAnalyzerTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/methodRef/findUsages/";
  
  public void testConstructorUsages() {
    final String testName = getTestName(false);
    configureByFile(BASE_PATH + testName + ".java");

    final PsiClass aClass = getJavaFacade().findClass(testName);
    assertNotNull(aClass);
    final PsiMethod[] constructors = aClass.getConstructors();
    assertEquals(1, constructors.length);
    Collection<PsiReference> references = MethodReferencesSearch.search(constructors[0]).findAll();
    assertEquals(1, references.size());
  }
}
