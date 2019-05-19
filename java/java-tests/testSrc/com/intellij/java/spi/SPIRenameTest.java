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
package com.intellij.java.spi;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.LightMultiFileTestCase;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;

public class SPIRenameTest extends LightMultiFileTestCase {
  public void testRenameProviderImplementation() {
    doRenameTest("Test1", "foo/Test.java");
  }
  
  public void testRenameProviderImplementationContainingClass() {
    doRenameTest("Test1", "foo/Test.java");
  }

  public void testRenamePackageWithImplementation() {
    doRenameTest("foo1", "bar/foo/FooRunnable.java");
  }

  private void doRenameTest(final String newName, final String relPath) {
    doTest(() -> {
      final VirtualFile file = myFixture.findFileInTempDir(relPath);
      assert file != null;
      myFixture.configureFromExistingVirtualFile(file);
      final PsiElement element = TargetElementUtil.findTargetElement(getEditor(),
                                                                     TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED |
                                                                     TargetElementUtil.ELEMENT_NAME_ACCEPTED);
      assert element != null;
      final PsiElement substitution = RenamePsiElementProcessor.forElement(element).substituteElementToRename(element, getEditor());
      assert substitution != null;
      new RenameProcessor(getProject(), substitution, newName, true, true).run();
    });
  }

   @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/spi/";
  }
}
