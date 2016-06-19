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
package com.intellij.codeInsight;

import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.search.GlobalSearchScope;

public class ClsGenerics18HighlightingTest extends ClsGenericsHighlightingTest {
  public void testIDEA121866() { doTest(); }

  public void testIDEA127714() { doTest(); }

  public void testoverload() { doTest(); }

  public void testIDEA151367() throws Exception {
    doTest();
  }

  public void testIDEA157254() throws Exception {
    doTest();
  }

  public void testOuterClassTypeArgs() throws Exception {
    doTest();
  }

  public void testCaptureContext() {
    String name = getTestName(false);
    addLibrary(name + ".jar", name + "-sources.jar");
    Project project = myFixture.getProject();
    PsiClass aClass = JavaPsiFacade.getInstance(project).findClass("a.Pair", GlobalSearchScope.allScope(project));
    assertNotNull(aClass);
    PsiFile containingFile = aClass.getContainingFile();
    PsiElement navigationElement = containingFile.getNavigationElement();
    assertInstanceOf(navigationElement, PsiFileImpl.class);
    myFixture.openFileInEditor(((PsiFile)navigationElement).getVirtualFile());
    myFixture.checkHighlighting();
  }

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_1_8;
  }
}
