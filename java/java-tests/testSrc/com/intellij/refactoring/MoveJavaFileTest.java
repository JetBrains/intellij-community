// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.util.registry.Registry;
import org.junit.Assert;

/**
 * @author ven
 */
public class MoveJavaFileTest extends MoveFileTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/moveFile/";
  }

  public void testPackageInfo() { doTest("pack2", "pack1/package-info.java"); }
  public void testConflict() {
    try {
      doTest("p2", "p1/B.java");
      fail("Conflict not detected!");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      Assert.assertEquals("Package-local class <b><code>B</code></b> will no longer be accessible from field <b><code>A.b</code></b>", e.getMessage());
    }
  }

  public static class BranchTest extends MoveJavaFileTest {
    @Override
    protected void setUp() throws Exception {
      super.setUp();
      Registry.get("run.refactorings.in.model.branch").setValue(true, getTestRootDisposable());
    }
  }
}