package org.jetbrains.ether;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 09.08.11
 * Time: 13:07
 * To change this template use File | Settings | File Templates.
 */
public class TestClassModifiers extends IncrementalTestCase {
    public TestClassModifiers() throws Exception {
        super("classModifiers");
    }

    public void testAddStatic() throws Exception {
        doTest();
    }

    public void testRemoveStatic() throws Exception {
            doTest();
        }

    public void testDecAccess() throws Exception {
        doTest();
    }

    public void testSetAbstract() throws Exception {
        doTest();
    }

    public void testDropAbstract() throws Exception {
        doTest();
    }

    public void testSetFinal() throws Exception {
        doTest();
    }

    public void testSetFinal1() throws Exception {
        doTest();
    }
}
