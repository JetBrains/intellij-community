package org.jetbrains.ether;

/**
 * @author: db
 * Date: 04.10.11
 */
public class PackageInfoTest extends IncrementalTestCase {
  public PackageInfoTest() throws Exception {
    super("packageInfo");
  }

  public void testPackageInfoNoRecompile() throws Exception {
    doTest();
  }

  public void testPackageInfoNoRecompile2() throws Exception {
    doTest();
  }

  public void testPackageInfoRecompileOnConstantChange() throws Exception {
    doTest();
  }
}
