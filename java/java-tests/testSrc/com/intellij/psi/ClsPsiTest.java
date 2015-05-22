/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.testFramework.LightIdeaTestCase;

public class ClsPsiTest extends LightIdeaTestCase {
  public void testKeywordAnnotatedClass() {
    PsiModifierList modList = getFile().getClasses()[0].getModifierList();
    assertNotNull(modList);
    assertEquals("@pkg.native", modList.getAnnotations()[0].getText());
  }

  private PsiJavaFile getFile() {
    return getFile(getTestName(false));
  }

  private static PsiJavaFile getFile(String name) {
    String path = PathManagerEx.getTestDataPath() + "/psi/cls/tree/" + name + ".class";
    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
    assertNotNull(path, file);
    PsiFile clsFile = PsiManager.getInstance(getProject()).findFile(file);
    assertTrue(String.valueOf(clsFile), clsFile instanceof ClsFileImpl);
    return (PsiJavaFile)clsFile;
  }
}