/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.compiler;

import com.intellij.openapi.compiler.CompileStatusNotification;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 21, 2005
 */
public class PackageInfoTest extends Jdk15CompilerTestCase{
public PackageInfoTest() {
    super("packageInfo");
  }
  public void testPackageInfoNoRecompile() throws Exception {doTest();}

  public void testPackageInfoNoRecompile2() throws Exception {doTest();}

  protected void doCompile(final CompileStatusNotification notification, int pass) {
    if (pass == 2){
      CompilerManagerImpl.clearPathsToCompile();
    }
    super.doCompile(notification, pass);
  }

  protected String[] getCompiledPathsToCheck() {
    return CompilerManagerImpl.getPathsToCompile();
  }
}
