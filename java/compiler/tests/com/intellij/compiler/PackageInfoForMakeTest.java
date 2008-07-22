/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.compiler;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 21, 2005
 */
public class PackageInfoForMakeTest extends Jdk15CompilerTestCase{
public PackageInfoForMakeTest() {
    super("packageInfo");
  }

  public void testPackageInfoRecompileOnConstantChange() throws Exception {doTest();}
}