package com.siyeh.igtest.controlflow.pointless_null_check;

public class PointlessNullCheck {

    String arg1 = "foo";

    public void testMethods(Object obj, Object obj1, Object obj2) {
        if(<warning descr="Unnecessary 'null' check before 'check()' call">obj != null</warning> && check(obj)) {
            System.out.println("ok");
        }
        if(<warning descr="Unnecessary 'null' check before 'check1()' call">obj1 != null</warning> && obj2 != null && check1(obj1, obj2)) {
            System.out.println("ok");
        }
        if(obj1 != null && <warning descr="Unnecessary 'null' check before 'check2()' call">obj2 != null</warning> && check2(obj1, obj2)) {
            System.out.println("ok");
        }
        if(obj != null && check1(obj, obj.toString())) {
            System.out.println("ok");
        }
    }

    private boolean check(Object obj) {
        if(obj == null) return false;
        return obj.hashCode() > 10;
    }

    private boolean check1(Object obj1, Object obj2) {
        if(obj1 == null) return false;
        return obj1.hashCode() > 10;
    }

    private boolean check2(Object obj1, Object obj2) {
        if(obj2 == null) return false;
        return obj2.hashCode() > 10;
    }

    void testQualified(Object obj) {
        if(<warning descr="Unnecessary 'null' check before 'check()' call">obj != null</warning> && check(obj)) System.out.println(1);
        if(<warning descr="Unnecessary 'null' check before 'check()' call">obj != null</warning> && this.check(obj)) System.out.println(1);
        // ctor called only if obj is non-null: removing it may change the semantics
        if(obj != null && new PointlessNullCheck().check(obj)) System.out.println(1);
        if(<warning descr="Unnecessary 'null' check before 'check2()' call">obj != null</warning> && check2(null, obj)) System.out.println(1);
        // argument side effect
        if(obj != null && check2(new PointlessNullCheck(), obj)) System.out.println(1);
    }

    void testEquals(String str) {
        if (<warning descr="Unnecessary 'null' check before 'equals()' call">str != null</warning> && "foo".equals(str)) {}
    }

  void x(Object x, Class<?> y) {
    if (x != "<error descr="Illegal escape character in string literal">\l</error>" && y.isInstance(x)) {}
  }
}
