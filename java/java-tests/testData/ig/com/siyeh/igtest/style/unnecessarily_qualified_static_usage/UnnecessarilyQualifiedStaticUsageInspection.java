package com.siyeh.igtest.style.unnecessarily_qualified_static_usage;

import java.util.List;
import java.util.ArrayList;
import static java.lang.Math.*;

public class UnnecessarilyQualifiedStaticUsageInspection {

    private static Object q;

    private static void r() {}
    class M {

        void r() {}

        void p() {
            int q;
            // class qualifier can't be removed
            UnnecessarilyQualifiedStaticUsageInspection.q = new Object();

            // can't be removed
            UnnecessarilyQualifiedStaticUsageInspection.r();

        }
    }

    void p() {
        // can be removed (not reported when "Only in static context" option is enabled)
        <warning descr="Unnecessarily qualified static access 'UnnecessarilyQualifiedStaticUsageInspection.q'">UnnecessarilyQualifiedStaticUsageInspection</warning>.q = new Object();

        // can be removed (not reported when "Only in static context" option is enabled)
        <warning descr="Unnecessarily qualified static method call 'UnnecessarilyQualifiedStaticUsageInspection.r()'">UnnecessarilyQualifiedStaticUsageInspection</warning>.r();
    }

    static void q() {
        // can be removed
        <warning descr="Unnecessarily qualified static access 'UnnecessarilyQualifiedStaticUsageInspection.q'">UnnecessarilyQualifiedStaticUsageInspection</warning>.q = new Object();

        final UnnecessarilyQualifiedStaticUsageInspection.M m;

        // can be removed
        <warning descr="Unnecessarily qualified static method call 'UnnecessarilyQualifiedStaticUsageInspection.r()'">UnnecessarilyQualifiedStaticUsageInspection</warning>.r();
    }
}

class TestUnnecessaryQualifiedNested
{
    static class Nested
    {
    }

    /**
     * A link to {@link TestUnnecessaryQualifiedNested.Nested} -- no warning here
     * <p/>
     * A link to {@link #doit(TestUnnecessaryQualifiedNested.Nested)} -- warns about an
     * unnecessary qualified static access but the quickfix does not work.
     *
     */
    public static void doit(Nested arg) {
        double pi = Math.PI;
    }
}
class X {
    private static final List<String> l = new ArrayList();
    static {
        <warning descr="Unnecessarily qualified static access 'X.l'">X</warning>.l.add("a");
        l.add("b");
        l.add("c");
    }
}

class InnerClassTest {
  public static int foo = 0;

  public static void bar() {
    System.out.println();
  }

  public static class Inner {
    public void test1() {
      <warning descr="Unnecessarily qualified static method call 'InnerClassTest.bar()'">InnerClassTest</warning>.bar();                     // (1)
      System.out.println(<warning descr="Unnecessarily qualified static access 'InnerClassTest.foo'">InnerClassTest</warning>.foo);   // (2)
    }
  }
}

class ForwardRefTest {
  private static final String FOO = ForwardRefTest.BAR;
  private static final String BAR = "BAR";
}