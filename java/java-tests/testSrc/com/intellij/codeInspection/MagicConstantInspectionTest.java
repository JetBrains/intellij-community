/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Apr 11, 2002
 * Time: 6:50:50 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.magicConstant.MagicConstantInspection;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.PsiClassImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.FileTreeAccessFilter;
import com.intellij.testFramework.InspectionTestCase;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class MagicConstantInspectionTest extends InspectionTestCase {

  private FileTreeAccessFilter myFilter;

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFilter = new FileTreeAccessFilter();
    PsiManagerEx.getInstanceEx(getProject()).setAssertOnFileLoadingFilter(myFilter, myTestRootDisposable);
  }

  @Override
  protected Sdk getTestProjectSdk() {
    return PsiTestUtil.addJdkAnnotations(super.getTestProjectSdk());
  }

  private void doTest() throws Exception {
    doTest("magic/" + getTestName(true), new LocalInspectionToolWrapper(new MagicConstantInspection()), "jdk 1.7");
  }

  @Override
  protected void setupRootModel(@NotNull String testDir, @NotNull VirtualFile[] sourceDir, String sdkName) {
    super.setupRootModel(testDir, sourceDir, sdkName);
    VirtualFile projectDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(testDir));
    // allow to load AST for all files to highlight
    VfsUtilCore.visitChildrenRecursively(projectDir, new VirtualFileVisitor() {
      @Override
      public boolean visitFile(@NotNull VirtualFile v) {
        myFilter.allowTreeAccessForFile(v);
        return super.visitFile(v);
      }
    });
    // and JFrame
    PsiClass cls = JavaPsiFacade.getInstance(getProject()).findClass("javax.swing.JFrame", GlobalSearchScope.allScope(getProject()));
    PsiClass aClass = (PsiClass)cls.getNavigationElement();
    assertTrue(aClass instanceof PsiClassImpl); // must to have sources

    myFilter.allowTreeAccessForFile(aClass.getContainingFile().getVirtualFile());
  }

  public void testSimple() throws Exception { doTest(); }
  // test that the optimisation for not loading AST works
  public void testWithLibrary() throws Exception { doTest(); }
}
