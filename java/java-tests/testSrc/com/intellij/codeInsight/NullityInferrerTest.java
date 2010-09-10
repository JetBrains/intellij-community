/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.inferNullity.NullityInferrer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * User: anna
 * Date: Sep 2, 2010
 */
public class NullityInferrerTest extends CodeInsightTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  //-----------------------params and return values---------------------------------
  public void testParameterPassed2NotNull() throws Exception {
    doTest(false);
  }

  public void testParameterCheckedForNull() throws Exception {
    doTest(false);
  }

  public void testParameterDereferenced() throws Exception {
    doTest(false);
  }

  //-----------------------fields---------------------------------------------------
  public void testFieldsAssignment() throws Exception {
    doTest(false);
  }

  //-----------------------methods---------------------------------------------------
  public void testMethodReturnValue() throws Exception {
    doTest(false);
  }


  private void doTest(boolean annotateLocalVariables) throws Exception  {
    final String nullityPath = "/codeInsight/nullityinferrer";
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final VirtualFile aLib = LocalFileSystem.getInstance().findFileByPath(getTestDataPath() + nullityPath + "/lib/annotations.jar");
        if (aLib != null) {
          final VirtualFile file = JarFileSystem.getInstance().getJarRootForLocalFile(aLib);
          if (file != null) {
            final ModifiableRootModel model = ModuleRootManager.getInstance(myModule).getModifiableModel();
            final LibraryTable libraryTable = model.getModuleLibraryTable();
            final Library library = libraryTable.createLibrary("test");

            final Library.ModifiableModel libraryModel = library.getModifiableModel();
            libraryModel.addRoot(file.getUrl(), OrderRootType.CLASSES);
            libraryModel.commit();
            model.commit();
          }
        }
      }
    });

    configureByFile(nullityPath + "/before" + getTestName(false) + ".java");
    final NullityInferrer nullityInferrer = new NullityInferrer(annotateLocalVariables, getProject());
    nullityInferrer.collect(getFile());
    nullityInferrer.apply(getProject());
    checkResultByFile(nullityPath + "/after" + getTestName(false)+ ".java");
  }
}
