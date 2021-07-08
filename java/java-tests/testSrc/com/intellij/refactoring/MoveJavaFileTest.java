// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;

/**
 * @author ven
 */
public class MoveJavaFileTest extends MoveFileTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/moveFile/";
  }

  public void testPackageInfo() { doTest("pack2", "pack1/package-info.java"); }
}