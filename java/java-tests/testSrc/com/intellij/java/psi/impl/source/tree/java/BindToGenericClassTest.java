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
package com.intellij.java.psi.impl.source.tree.java;

import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.java.psi.GenericsFixture.genericsFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class BindToGenericClassTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> genericsFixture = genericsFixture();

  @BeforeEach
  void setUp() {
    JavaCodeStyleSettings.getInstance(genericsFixture.get().getProject()).USE_FQ_CLASS_NAMES = true;
  }

  @Test
  public void testReferenceElement() {
    HeavyJavaCodeInsightFixture fixture = genericsFixture.get();
    final JavaPsiFacade manager = fixture.getJavaFacade();
    final PsiClass classA = ReadAction.computeBlocking(
      () -> manager.getElementFactory().createClassFromText("class A extends List<String>{}", null).getInnerClasses()[0]);
    final PsiClass classTestList = ReadAction.computeBlocking(
      () -> manager.findClass("test.List", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(fixture.getModule())));
    assertNotNull(classTestList);
    WriteCommandAction.writeCommandAction(fixture.getProject()).run(
      () -> classA.getExtendsList().getReferenceElements()[0].bindToElement(classTestList));
    assertEquals("class A extends test.List<String>{}", ReadAction.computeBlocking(() -> classA.getText()));
  }

  @Test
  public void testReference() {
    HeavyJavaCodeInsightFixture fixture = genericsFixture.get();
    final JavaPsiFacade manager = fixture.getJavaFacade();
    final PsiExpression psiExpression = ReadAction.computeBlocking(() -> manager.getElementFactory().createExpressionFromText("List", null));
    final PsiClass classTestList = ReadAction.computeBlocking(
      () -> manager.findClass("test.List", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(fixture.getModule())));
    assertNotNull(classTestList);
    final PsiElement result = WriteCommandAction.writeCommandAction(fixture.getProject()).compute(
      () -> ((PsiReferenceExpression) psiExpression).bindToElement(classTestList));
    assertEquals("test.List", ReadAction.computeBlocking(() -> result.getText()));
  }
}
