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
package com.intellij.psi.impl.file.impl;

import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

import java.io.IOException;

public class TempFileSystemTest extends LightPlatformCodeInsightFixtureTestCase {
  public void testMove() {
    ProjectRootManager rootManager = ProjectRootManager.getInstance(getProject());
    VirtualFile sourceRoot = rootManager.getContentSourceRoots()[0];
    PsiManager psiManager = PsiManager.getInstance(getProject());
    PsiDirectory psiSourceRoot = psiManager.findDirectory(sourceRoot);
    PsiFile psiFile = psiSourceRoot.createFile("TestDocument.xml");
    try {
      psiFile.getVirtualFile().move(this, psiSourceRoot.createSubdirectory("com").getVirtualFile());
    } catch (IOException e) {
      e.printStackTrace();
    }
    assertTrue(psiFile.isValid());
    psiFile.delete();
    assertFalse(psiFile.isValid());
  }
}