/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.compiler;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 21, 2005
 */
public class PackageInfoTestForMake extends Jdk15CompilerTestCase{
public PackageInfoTestForMake() {
    super("packageInfo");
  }

  public void testPackageInfoRecompileOnConstantChange() throws Exception {doTest();}
}