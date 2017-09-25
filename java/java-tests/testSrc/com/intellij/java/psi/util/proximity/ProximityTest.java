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

import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.util.proximity.PsiProximityComparator;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class ProximityTest extends JavaCodeInsightFixtureTestCase {

  public void testSameSourceRoot() throws Throwable {
    final TempDirTestFixture root1 = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture();
    final TempDirTestFixture root2 = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture();

    root1.setUp();
    root2.setUp();

    try {
      new WriteCommandAction(getProject()) {
        @Override
        protected void run(@NotNull Result result) {
          PsiTestUtil.addSourceContentToRoots(myModule, root1.getFile(""));
          PsiTestUtil.addSourceContentToRoots(myModule, root2.getFile(""));
        }
      }.execute();

      final VirtualFile file1 = root1.createFile("buy.txt", "");
      final VirtualFile file2 = root2.createFile("buy.txt", "");
      final VirtualFile ctx = root2.createFile("ctx.txt", "");

      final PsiProximityComparator comparator = new PsiProximityComparator(getPsiManager().findFile(ctx));
      assertTrue(comparator.compare(getPsiManager().findFile(file1), getPsiManager().findFile(file2)) > 0);
      assertTrue(comparator.compare(getPsiManager().findFile(file2), getPsiManager().findFile(file1)) < 0);
    }
    finally {
      root1.tearDown();
      root2.tearDown();
    }
  }

}
