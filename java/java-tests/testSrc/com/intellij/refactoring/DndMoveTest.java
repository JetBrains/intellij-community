/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.refactoring;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.refactoring.move.MoveHandler;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author anna
 * @since Aug 31, 2010
 */
public class DndMoveTest extends CodeInsightTestCase {
  @NotNull
  @Override
  protected String getTestRoot() {
    return "/refactoring/dndMove/";
  }

  public void testPublicJavaClass() throws Exception {
    doTest("d", () -> getJavaFacade().findClass("d.MyClass"), true);
  }

  public void testSecondJavaClass() throws Exception {
    doTest("d", () -> getJavaFacade().findClass("d.Second"), false);
  }

  private void doTest(final String targetDirName, final Computable<PsiElement> source, final boolean expected) throws Exception {
    String testName = getTestName(true);
    String root = getTestDataPath() + getTestRoot() + testName;
    VirtualFile rootDir = PsiTestUtil.createTestProjectStructure(myProject, myModule, root, myFilesToDelete, false);
    PsiTestUtil.addSourceContentToRoots(myModule, rootDir);
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    final VirtualFile child1 = rootDir.findChild(targetDirName);
    assertNotNull("File " + targetDirName + " not found", child1);
    final PsiDirectory targetDirectory = myPsiManager.findDirectory(child1);
    assertEquals(expected, MoveHandler.isMoveRedundant(source.compute(), targetDirectory));
  }
}