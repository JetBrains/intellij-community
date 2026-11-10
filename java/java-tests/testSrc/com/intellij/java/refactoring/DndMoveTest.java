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

import com.intellij.JavaTestUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.move.MoveHandler;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author anna
 */
@TestApplication
public class DndMoveTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/refactoring/dndMove/");
  }

  @Test
  public void testPublicJavaClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    doTest("d", () -> myFixture.findClass("d.MyClass"), true);
  }

  @Test
  public void testSecondJavaClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    doTest("d", () -> myFixture.findClass("d.Second"), false);
  }

  private void doTest(final String targetDirName, final Computable<PsiElement> source, final boolean expected) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFile rootDir = myFixture.copyDirectoryToProject(myFixture.getTestName(true), "");
    final VirtualFile child1 = rootDir.findChild(targetDirName);
    assertNotNull(child1, "File " + targetDirName + " not found");
    ReadAction.runBlocking(() -> {
      final PsiDirectory targetDirectory = PsiManager.getInstance(myFixture.getProject()).findDirectory(child1);
      assertEquals(expected, MoveHandler.isMoveRedundant(source.compute(), targetDirectory));
    });
  }
}