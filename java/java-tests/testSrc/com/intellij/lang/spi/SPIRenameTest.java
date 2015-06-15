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
package com.intellij.lang.spi;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.MultiFileTestCase;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 */
public class SPIRenameTest extends MultiFileTestCase {
  public void testRenameProviderImplementation() throws Exception {
    doRenameTest("Test.java", "Test1");
  }
  
  public void testRenameProviderImplementationContainingClass() throws Exception {
    doRenameTest("Test.java", "Test1");
  }

  private void doRenameTest(final String editorFile, final String newName) throws Exception {
    doTest(new PerformAction() {
      @Override
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        final VirtualFile file = rootDir.findFileByRelativePath("foo/" + editorFile);
        assert file != null;
        configureByExistingFile(file);
        final PsiElement element = TargetElementUtil.findTargetElement(myEditor,
                                                                       TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED |
                                                                       TargetElementUtil.ELEMENT_NAME_ACCEPTED);
        assert element != null;
        final PsiElement substitution = RenamePsiElementProcessor.forElement(element).substituteElementToRename(element, myEditor);
        assert substitution != null;
        new RenameProcessor(getProject(), substitution, newName, true, true).run();
      }
    });
  }

  @NotNull
  @Override
  protected String getTestRoot() {
    return  "/spi/";
  }
}
