// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.ThrowableRunnable;

import java.io.File;

public abstract class LightMultiFileTestCase extends LightJavaCodeInsightFixtureTestCase {
  protected void doTest(final ThrowableRunnable<? extends Exception> performAction) {
    doTest(performAction, getTestName(true));
  }

  protected void doTest(final ThrowableRunnable<? extends Exception> performAction, final boolean lowercaseFirstLetter) {
    doTest(performAction, getTestName(lowercaseFirstLetter));
  }

  protected void doTest(final ThrowableRunnable<? extends Exception> performAction, final String testName) {
    try {
      VirtualFile actualDirectory = myFixture.copyDirectoryToProject(testName + "/before", "");

      performAction.run();

      VirtualFile rootAfter = LocalFileSystem.getInstance().findFileByPath(getTestDataPath().replace(File.separatorChar, '/') + testName + "/after");
      assertNotNull(rootAfter);

      PlatformTestUtil.assertDirectoriesEqual(rootAfter, actualDirectory);
    }
    catch (RuntimeException e) {
      throw e;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
