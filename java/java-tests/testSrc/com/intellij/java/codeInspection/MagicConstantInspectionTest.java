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

package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.magicConstant.MagicConstantInspection;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.PsiClassImpl;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MagicConstantInspectionTest extends LightCodeInsightFixtureTestCase {
  private static final LightProjectDescriptor DESCRIPTOR = new LightProjectDescriptor() {
    @Nullable
    @Override
    public Sdk getSdk() {
      // has to have JFrame and sources
      return PsiTestUtil.addJdkAnnotations(IdeaTestUtil.getMockJdk17());
    }
  };

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/magic/";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return DESCRIPTOR;
  }

  public void testSimple() { doTest(); }
  
  public void testManyConstantSources() { doTest(); }
  // test that the optimisation for not loading AST works
  public void testWithLibrary() { doTest(); }
  public void testSpecialCases() { doTest(); }

  private void doTest() {
    myFixture.configureByFile(getTestName(false) + ".java");

    PsiClass calendarClass = myFixture.getJavaFacade().findClass(CommonClassNames.JAVA_UTIL_CALENDAR);
    assertNotNull("No Calendar class in mockJDK", calendarClass);
    PsiElement calendarSource = calendarClass.getNavigationElement();
    assertTrue(calendarSource instanceof PsiClassImpl);
    myFixture.allowTreeAccessForFile(calendarSource.getContainingFile().getVirtualFile());

    myFixture.enableInspections(new MagicConstantInspection());
    myFixture.testHighlighting(true, false, false);
  }
}
