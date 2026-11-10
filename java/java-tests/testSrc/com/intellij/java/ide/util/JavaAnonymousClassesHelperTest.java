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
import com.intellij.ide.util.JavaAnonymousClassesHelper;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Konstantin Bulenkov
 */
@TestApplication
public class JavaAnonymousClassesHelperTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/anonymous/");
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
  }

  @Test
  public void testSimple() {
    doTest(7);
  }
  @Test
  public void testSimpleInConstructor() {
    doTest(2);
  }
  @Test
  public void testInsideAnonymousMethod() {
    doTest(1);
  }
  @Test
  public void testAnonymousParameterInAnonymousConstructor() {
    doTest(1);
  }
  @Test
  public void testAnonymousParameterInAnonymousConstructor2() {
    doTest(2);
  }

  @Test
  public void testNestedConstructorArguments() {
    assertBinaryNames("NestedConstructorArguments$3", "NestedConstructorArguments$2",
                      "NestedConstructorArguments$1", "NestedConstructorArguments$3$1");
  }

  @Test
  public void testAnonymousArgumentScopes() {
    assertBinaryNames("AnonymousArgumentScopes$1", "AnonymousArgumentScopes$1Local$1", "AnonymousArgumentScopes$2");
  }

  private void assertBinaryNames(String... expectedNames) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ReadAction.runBlocking(() -> {
      var classes = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), PsiAnonymousClass.class);
      assertEquals(List.of(expectedNames), ContainerUtil.map(classes, ClassUtil::getBinaryClassName));
    });
  }

  @SuppressWarnings("ConstantConditions")
  private void doTest(int num) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ReadAction.runBlocking(() -> {
      final PsiElement element = PsiUtilBase.getElementAtCaret(myFixture.getEditor()).getParent().getParent();

      assert element instanceof PsiAnonymousClass : "There should be anonymous class at caret but " + element + " found";

      assertEquals("$" + num, JavaAnonymousClassesHelper.getName((PsiAnonymousClass)element));
    });
  }
}
