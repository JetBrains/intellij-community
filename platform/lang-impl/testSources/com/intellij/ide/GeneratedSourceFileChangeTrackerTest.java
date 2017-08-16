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
package com.intellij.ide;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class GeneratedSourceFileChangeTrackerTest extends CodeInsightFixtureTestCase {
  private final GeneratedSourcesFilter myGeneratedSourcesFilter = new GeneratedSourcesFilter() {
    @Override
    public boolean isGeneratedSource(@NotNull VirtualFile file, @NotNull Project project) {
      return file.getName().startsWith("Gen");
    }
  };

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Extensions.getRootArea().getExtensionPoint(GeneratedSourcesFilter.EP_NAME).registerExtension(myGeneratedSourcesFilter);
  }

  @Override
  protected void tearDown() throws Exception {
    Extensions.getRootArea().getExtensionPoint(GeneratedSourcesFilter.EP_NAME).unregisterExtension(myGeneratedSourcesFilter);
    super.tearDown();
  }

  public void testChangeOrdinary() {
    PsiFile file = myFixture.configureByText("Ordinary.txt", "");
    myFixture.type('a');
    assertFalse(isEditedGeneratedFile(file));
  }

  public void testChangeGenerated() {
    PsiFile file = myFixture.configureByText("Gen.txt", "");
    myFixture.type('a');
    assertTrue(isEditedGeneratedFile(file));
  }

  public void testChangeGeneratedExternally() {
    PsiFile file = myFixture.configureByText("Gen.txt", "");
    myFixture.saveText(file.getVirtualFile(), "abc");
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    assertFalse(isEditedGeneratedFile(file));
  }

  private boolean isEditedGeneratedFile(PsiFile file) {
    return getTracker().isEditedGeneratedFile(file.getVirtualFile());
  }

  private GeneratedSourceFileChangeTracker getTracker() {
    return GeneratedSourceFileChangeTracker.getInstance(getProject());
  }
}
