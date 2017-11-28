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
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.MultiFileTestCase;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor;
import com.intellij.refactoring.move.moveClassesOrPackages.MultipleRootsMoveDestination;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NotNull;

/**
 *  @author dsl
 */
public class MovePackageMultirootTest extends MultiFileTestCase {

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @NotNull
  @Override
  protected String getTestRoot() {
    return "/refactoring/movePackageMultiroot/";
  }

  public void testMovePackage() {
    doTest(createAction(new String[]{"pack1"}, "target"));
  }

  private PerformAction createAction(final String[] packageNames, final String targetPackageName) {
    return (rootDir, rootAfter) -> {
      final PsiManager manager = PsiManager.getInstance(myProject);
      PsiPackage[] sourcePackages = new PsiPackage[packageNames.length];
      for (int i = 0; i < packageNames.length; i++) {
        String packageName = packageNames[i];
        sourcePackages[i] = JavaPsiFacade.getInstance(manager.getProject()).findPackage(packageName);
        assertNotNull(sourcePackages[i]);
      }
      PsiPackage targetPackage = JavaPsiFacade.getInstance(manager.getProject()).findPackage(targetPackageName);
      assertNotNull(targetPackage);
      new MoveClassesOrPackagesProcessor(myProject, sourcePackages,
                                         new MultipleRootsMoveDestination(new PackageWrapper(targetPackage)),
                                         true, true, null).run();
      FileDocumentManager.getInstance().saveAllDocuments();
    };
  }

  @Override
  protected void prepareProject(VirtualFile rootDir) {
    PsiTestUtil.addContentRoot(myModule, rootDir);
    final VirtualFile[] children = rootDir.getChildren();
    for (VirtualFile child : children) {
      if (child.getName().startsWith("src")) {
        PsiTestUtil.addSourceRoot(myModule, child);
      }
    }
  }
}
