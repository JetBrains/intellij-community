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
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.MultiFileTestCase;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor;
import com.intellij.refactoring.move.moveClassesOrPackages.MultipleRootsMoveDestination;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;

/**
 *  @author dsl
 */
public class MovePackageMultirootTest extends MultiFileTestCase {

  @NotNull
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
    doTest((rootDir, rootAfter) -> {
      JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());

      PsiPackage pack1 = facade.findPackage("pack1");
      assertSize(2, pack1.getDirectories());
      assertSize(1, facade.findPackage("pack1.inside").getDirectories());
      assertSize(1, facade.findPackage("pack1Unrelated").getDirectories());

      PsiPackage[] sourcePackages = new PsiPackage[]{pack1};
      PsiPackage targetPackage = facade.findPackage("target");
      new MoveClassesOrPackagesProcessor(myProject, sourcePackages,
                                         new MultipleRootsMoveDestination(new PackageWrapper(targetPackage)),
                                         true, true, null).run();
      FileDocumentManager.getInstance().saveAllDocuments();

      assertSize(2, facade.findPackage("target.pack1").getDirectories());
      assertSize(1, facade.findPackage("target.pack1.inside").getDirectories());
      assertSize(1, facade.findPackage("pack1Unrelated").getDirectories());
    });
  }

  @Override
  protected void prepareProject(VirtualFile rootDir) {
    PsiTestUtil.addContentRoot(myModule, rootDir);
    final VirtualFile[] children = rootDir.getChildren();
    for (VirtualFile child : children) {
      String name = child.getName();
      if (name.startsWith("src")) {
        String prefix = name.equals("srcPrefix1") ? "pack1.inside" :
                        name.equals("srcPrefix2") ? "pack1Unrelated" :
                        "";
        PsiTestUtil.addSourceRoot(myModule, child, JavaSourceRootType.SOURCE,
                                  JpsJavaExtensionService.getInstance().createSourceRootProperties(prefix, false));
      }
    }
  }
}
