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

package com.intellij.java.refactoring;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.refactoring.changeSignature.JavaChangeSignatureHandler;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class ChangeSignatureTargetTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  @Test
  public void testInMethodParameters() {
    doTest("foo");
  }

  @Test
  public void testInMethodArguments() {
    doTest("foo");
  }

  @Test
  public void testInClassTypeParameters() {
    doTest("A1");
  }

  @Test
  public void testInTypeArguments() {
    doTest("A1");
  }

  @Test
  public void testDisabledForEnum() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/changeSignatureTarget/" + myFixture.getTestName(true) + ".java");
    assertNull(ReadAction.computeBlocking(() -> new JavaChangeSignatureHandler().findTargetMember(myFixture.getFile(), myFixture.getEditor())));
  }

  private void doTest(String expectedMemberName) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String basePath = "/refactoring/changeSignatureTarget/" + myFixture.getTestName(true);
    @NonNls final String filePath = basePath + ".java";
    myFixture.configureByFile(filePath);
    ReadAction.runBlocking(() -> {
      final PsiElement member = new JavaChangeSignatureHandler().findTargetMember(myFixture.getFile(), myFixture.getEditor());
      assertNotNull(member);
      assertEquals(expectedMemberName, ((PsiMember)member).getName());
    });
  }
}
