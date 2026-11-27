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
package com.intellij.java.psi.util.proximity;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.proximity.PsiProximityComparator;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.javaModuleCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class ProximityTest {
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture = javaModuleCodeInsightFixture(PathManager.getHomePath());

  @Test
  public void testSameSourceRoot() throws Throwable {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final TempDirTestFixture root1 = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture();
    final TempDirTestFixture root2 = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture();

    root1.setUp();
    root2.setUp();

    try {
      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
        PsiTestUtil.addSourceContentToRoots(myFixture.getModule(), root1.getFile(""));
        PsiTestUtil.addSourceContentToRoots(myFixture.getModule(), root2.getFile(""));
      });

      final VirtualFile file1 = root1.createFile("buy.txt", "");
      final VirtualFile file2 = root2.createFile("buy.txt", "");
      final VirtualFile ctx = root2.createFile("ctx.txt", "");

      final PsiProximityComparator comparator = ReadAction.computeBlocking(() -> new PsiProximityComparator(PsiManager.getInstance(myFixture.getProject()).findFile(ctx)));
      ReadAction.runBlocking(() -> {
        assertTrue(comparator.compare(PsiManager.getInstance(myFixture.getProject()).findFile(file1), PsiManager.getInstance(myFixture.getProject()).findFile(file2)) > 0);
        assertTrue(comparator.compare(PsiManager.getInstance(myFixture.getProject()).findFile(file2), PsiManager.getInstance(myFixture.getProject()).findFile(file1)) < 0);
      });
    }
    finally {
      root1.tearDown();
      root2.tearDown();
    }
  }

}
