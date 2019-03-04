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

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.move.MoveHandler;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

/**
 * @author anna
 */
public class DndMoveTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getTestDataPath() + "/refactoring/dndMove/";
  }

  public void testPublicJavaClass() {
    doTest("d", () -> myFixture.findClass("d.MyClass"), true);
  }

  public void testSecondJavaClass() {
    doTest("d", () -> myFixture.findClass("d.Second"), false);
  }

  private void doTest(final String targetDirName, final Computable<PsiElement> source, final boolean expected) {
    VirtualFile rootDir = myFixture.copyDirectoryToProject(getTestName(true), "");
    final VirtualFile child1 = rootDir.findChild(targetDirName);
    assertNotNull("File " + targetDirName + " not found", child1);
    final PsiDirectory targetDirectory = getPsiManager().findDirectory(child1);
    assertEquals(expected, MoveHandler.isMoveRedundant(source.compute(), targetDirectory));
  }
}