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
import com.intellij.refactoring.LightMultiFileFixture;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.refactoring.LightMultiFileFixtureKt.lightMultiFileFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class SPIRenameTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightMultiFileFixture> multiFile =
    lightMultiFileFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/spi/");

  @Test
  public void testRenameProviderImplementation() {
    doRenameTest("Test1", "foo/Test.java");
  }
  
  @Test
  public void testRenameProviderImplementationContainingClass() {
    doRenameTest("Test1", "foo/Test.java");
  }

  @Test
  public void testRenamePackageWithImplementation() {
    doRenameTest("foo1", "bar/foo/FooRunnable.java");
  }

  private void doRenameTest(final String newName, final String relPath) {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    multiFile.get().doTest(() -> {
      final VirtualFile file = myFixture.findFileInTempDir(relPath);
      assert file != null;
      myFixture.configureFromExistingVirtualFile(file);
      final PsiElement element = TargetElementUtil.findTargetElement(myFixture.getEditor(),
                                                                     TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED |
                                                                     TargetElementUtil.ELEMENT_NAME_ACCEPTED);
      assert element != null;
      final PsiElement substitution = RenamePsiElementProcessor.forElement(element).substituteElementToRename(element, myFixture.getEditor());
      assert substitution != null;
      rename(substitution, newName);
    });
  }

  /** Renames {@code element}, searching in comments and in text occurrences; a subclass renames with another engine. */
  protected void rename(@NotNull PsiElement element, @NotNull String newName) {
    new RenameProcessor(element.getProject(), element, newName, true, true).run();
  }
}
