/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.ide.util;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.ide.util.SuperMethodWarningUtil;
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
