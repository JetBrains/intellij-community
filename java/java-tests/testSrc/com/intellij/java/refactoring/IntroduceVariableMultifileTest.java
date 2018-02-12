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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.MultiFileTestCase;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.testFramework.PlatformTestCase;
import org.jetbrains.annotations.NotNull;

/**
 *  @author dsl
 */
@PlatformTestCase.WrapInCommand
public class IntroduceVariableMultifileTest extends MultiFileTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    LanguageLevelProjectExtension.getInstance(myJavaFacade.getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
  }

  @NotNull
  @Override
  protected String getTestRoot() {
    return "/refactoring/introduceVariable/";
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testSamePackageRef() {
    doTest(
      createAction("pack1.A",
                   new MockIntroduceVariableHandler("b", false, false, false, "pack1.B")
      )
    );
  }

  public void testGenericTypeWithInner() {
    doTest(
      createAction("test.Client",
                   new MockIntroduceVariableHandler("l", false, true, true, "test.List<test.A.B>")
      )
    );
  }

  public void testGenericTypeWithInner1() {
    doTest(
      createAction("test.Client",
                   new MockIntroduceVariableHandler("l", false, true, true, "test.List<test.A.B>")
      )
    );
  }

  public void testGenericWithTwoParameters() {
    doTest(
      createAction("Client",
                   new MockIntroduceVariableHandler("p", false, false, true,
                                                    "util.Pair<java.lang.String,util.Pair<java.lang.Integer,java.lang.Boolean>>")
      )
    );
  }

  public void testGenericWithTwoParameters2() {
    doTest(
      createAction("Client",
                   new MockIntroduceVariableHandler("p", false, false, true,
                                                    "Pair<java.lang.String,Pair<java.lang.Integer,java.lang.Boolean>>")
      )
    );
  }

  PerformAction createAction(final String className, final IntroduceVariableBase testMe) {
    return (vroot, rootAfter) -> {
      final JavaPsiFacade psiManager = getJavaFacade();
      final PsiClass aClass = psiManager.findClass(className, GlobalSearchScope.allScope(myProject));
      assertTrue(className + " class not found", aClass != null);
      final PsiFile containingFile = aClass.getContainingFile();
      final VirtualFile virtualFile = containingFile.getVirtualFile();
      assertTrue(virtualFile != null);
      final Editor editor = createEditor(virtualFile);
      setupCursorAndSelection(editor);
      testMe.invoke(myProject, editor, containingFile, null);
      FileDocumentManager.getInstance().saveAllDocuments();
    };
  }
}
