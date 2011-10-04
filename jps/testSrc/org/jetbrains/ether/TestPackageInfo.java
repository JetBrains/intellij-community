package org.jetbrains.ether;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 04.10.11
 * Time: 14:48
 * To change this template use File | Settings | File Templates.
 */
public class TestPackageInfo extends IncrementalTestCase {
    public TestPackageInfo() throws Exception {
        super("packageInfo");
    }

    public void testPackageInfoNoRecompile() throws Exception{
        doTest();
    }

    public void testPackageInfoNoRecompile2() throws Exception{
        doTest();
    }

    public void testPackageInfoRecompileOnConstantChange() throws Exception{
        doTest();
    }
}
