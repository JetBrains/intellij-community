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

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class ExtendsBoundListTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  @Test
  public void testRemoveBoundFromFront() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    final PsiTypeParameter typeParameter = getTypeParameter();
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> typeParameter.getExtendsList().getReferenceElements()[0].delete());

    check();
  }

  @Test
  public void testRemoveBoundFromEnd() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    final PsiTypeParameter typeParameter = getTypeParameter();
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> typeParameter.getExtendsList().getReferenceElements()[1].delete());

    check();
  }

  @Test
  public void testRemoveBoundFromMiddle() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    final PsiTypeParameter typeParameter = getTypeParameter();
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> typeParameter.getExtendsList().getReferenceElements()[1].delete());

    check();
  }

  @Test
  public void testAddBoundInTheMiddle() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    final PsiTypeParameter typeParameter = getTypeParameter();
    final PsiReferenceList extendsList = ReadAction.computeBlocking(typeParameter::getExtendsList);
    final PsiJavaCodeReferenceElement reference = ReadAction.computeBlocking(() -> {
      final PsiClass cloneableClass = myFixture.getJavaFacade().findClass("java.lang.Cloneable");
      assertNotNull(cloneableClass);
      return myFixture.getJavaFacade().getElementFactory().createClassReferenceElement(cloneableClass);
    });
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      extendsList.addAfter(reference, extendsList.getReferenceElements()[0]);
    });

    check();
  }

  @Test
  public void testAddBoundInFront() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    final PsiTypeParameter typeParameter = getTypeParameter();
    final PsiReferenceList extendsList = ReadAction.computeBlocking(typeParameter::getExtendsList);
    final PsiJavaCodeReferenceElement reference = ReadAction.computeBlocking(() -> {
      final PsiClass cloneableClass = myFixture.getJavaFacade().findClass("java.lang.Cloneable");
      assertNotNull(cloneableClass);
      return myFixture.getJavaFacade().getElementFactory().createClassReferenceElement(cloneableClass);
    });
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      extendsList.addBefore(reference, extendsList.getReferenceElements()[0]);
    });

    check();
  }

  @Test
  public void testAddBoundInEnd() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    final PsiTypeParameter typeParameter = getTypeParameter();
    final PsiReferenceList extendsList = ReadAction.computeBlocking(typeParameter::getExtendsList);
    final PsiJavaCodeReferenceElement reference = ReadAction.computeBlocking(() -> {
      final PsiClass cloneableClass = myFixture.getJavaFacade().findClass("java.lang.Cloneable");
      assertNotNull(cloneableClass);
      return myFixture.getJavaFacade().getElementFactory().createClassReferenceElement(cloneableClass);
    });
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      extendsList.addBefore(reference, null);
    });

    check();
  }

  @Test
  public void testAddBound() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    final PsiTypeParameter typeParameter = getTypeParameter();
    final PsiReferenceList extendsList = ReadAction.computeBlocking(typeParameter::getExtendsList);
    final PsiJavaCodeReferenceElement reference = ReadAction.computeBlocking(() -> {
      final PsiClass cloneableClass = myFixture.getJavaFacade().findClass(CommonClassNames.JAVA_LANG_RUNNABLE);
      assertNotNull(cloneableClass);
      return myFixture.getJavaFacade().getElementFactory().createClassReferenceElement(cloneableClass);
    });
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      extendsList.add(reference);
    });

    check();
  }

  private void check() {
    outputFile(daemonFixture.get().getFixture().getTestName(true) + "_after.java");
  }

  private PsiTypeParameter getTypeParameter() {
    PsiJavaFile file = inputFile(daemonFixture.get().getFixture().getTestName(true) + ".java");
    return ReadAction.computeBlocking(() -> {
      final PsiClass aClass = file.getClasses()[0];
      return aClass.getTypeParameters()[0];
    });
  }

  private void outputFile(String filename) {
    daemonFixture.get().getFixture().checkResultByFile("/psi/impl/extendsBoundList/" + filename);
  }

  private PsiJavaFile inputFile(String filename) {
    return (PsiJavaFile)daemonFixture.get().configureByFile("/psi/impl/extendsBoundList/" + filename);
  }
}
