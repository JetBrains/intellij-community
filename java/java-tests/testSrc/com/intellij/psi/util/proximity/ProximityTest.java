/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.util.proximity;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.TempDirTestFixture;

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
        protected void run(Result result) throws Throwable {
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
