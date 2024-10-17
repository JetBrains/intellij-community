import java.lang.reflect.Method;

import java.lang.reflect.Constructor;

class ConstructorParamCount {
  void varargSignature() throws Exception {
    Class<?> cls = Test.class;

    Constructor c1 = cls.getConstructor(int.class);
    Constructor c2 = cls.getConstructor(int.class, String.class);
    Constructor c3 = cls.getConstructor(int.class, String.class, String.class);

    c1.newInstance(<warning descr="Reflectively called method requires one argument">42, "abc"</warning>);
    c2.newInstance(42, "abc");
    c3.newInstance(<warning descr="Reflectively called method requires 3 arguments">42, "abc"</warning>);

    c1.newInstance(<warning descr="Single-item array is expected">new Object[]{42, "abc"}</warning>);
    c2.newInstance(new Object[]{42, "abc"});
    c3.newInstance(<warning descr="3 array items are expected">new Object[]{42, "abc"}</warning>);

    c1.newInstance<warning descr="Reflectively called method requires one argument">()</warning>;
    c2.newInstance<warning descr="Reflectively called method requires 2 arguments">()</warning>;
    c3.newInstance<warning descr="Reflectively called method requires 3 arguments">()</warning>;

    cls.getConstructor(String.class).newInstance(new String[] {"abc"});
  }

  void arraySignatutre() throws Exception {
    Class<?> cls = Test.class;

    Constructor c1 = cls.getConstructor(new Class[]{int.class});
    Constructor c2 = cls.getConstructor(new Class[]{int.class, String.class});
    Constructor c3 = cls.getConstructor(new Class[]{int.class, String.class, String.class});

    c1.newInstance(<warning descr="Reflectively called method requires one argument">42, "abc"</warning>);
    c2.newInstance(42, "abc");
    c3.newInstance(<warning descr="Reflectively called method requires 3 arguments">42, "abc"</warning>);

    c1.newInstance(<warning descr="Single-item array is expected">new Object[]{42, "abc"}</warning>);
    c2.newInstance(new Object[]{42, "abc"});
    c3.newInstance(<warning descr="3 array items are expected">new Object[]{42, "abc"}</warning>);

    c1.newInstance<warning descr="Reflectively called method requires one argument">()</warning>;
    c2.newInstance<warning descr="Reflectively called method requires 2 arguments">()</warning>;
    c3.newInstance<warning descr="Reflectively called method requires 3 arguments">()</warning>;

    cls.getConstructor(new Class[]{String.class}).newInstance(new String[] {"abc"});
  }

  void manyArguments() throws Exception {
    Class<?> cls = M.class;

    Constructor m0 = cls.getConstructor();
    Constructor m1 = cls.getConstructor(int.class);
    Constructor m2 = cls.getConstructor(int.class, short.class);
    Constructor m3 = cls.getConstructor(int.class, short.class, long.class);
    Constructor m4 = cls.getConstructor(int.class, short.class, long.class, float.class);
    Constructor m5 = cls.getConstructor(int.class, short.class, long.class, float.class, double.class);

    m0.newInstance(<warning descr="Reflectively called method requires no arguments">0, 0</warning>);
    m1.newInstance(<warning descr="Argument is not assignable to 'int'">"abc"</warning>);
    m2.newInstance(0, <warning descr="Argument is not assignable to 'short'">"abc"</warning>);
    m3.newInstance(0, <warning descr="Argument is not assignable to 'short'">0</warning>, <warning descr="Argument is not assignable to 'long'">"abc"</warning>);
    m4.newInstance(0, <warning descr="Argument is not assignable to 'short'">0</warning>, 0, <warning descr="Argument is not assignable to 'float'">"abc"</warning>);
    m5.newInstance(0, <warning descr="Argument is not assignable to 'short'">0</warning>, 0, 0, <warning descr="Argument is not assignable to 'double'">"abc"</warning>);

    Constructor m = cls.getConstructor(new Class[0]);
    m.newInstance(new Object[0]);
    m.newInstance(new Object[]{});
    m.newInstance(<warning descr="Empty array is expected">new Object[] {"abc"}</warning>);

    m = cls.getConstructor(new Class[]{});
    m.newInstance(new Object[]{});
  }

  class Test {
    public Test(int n) {}
    public Test(int n, String s) {}
    public Test(int n, String s, String t) {}

    public Test(String s) {}
  }

  class M{
    public M() {}
    public M(int a1) {}
    public M(int a1, short a2) {}
    public M(int a1, short a2, long a3) {}
    public M(int a1, short a2, long a3, float a4) {}
    public M(int a1, short a2, long a3, float a4, double a5) {}
  }
}