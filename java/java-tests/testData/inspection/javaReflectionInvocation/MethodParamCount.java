import java.lang.reflect.Method;

class MethodParamCount {
  void varargSignature() throws Exception {
    Class<?> cls = Test.class;
    Object obj = new Test();

    Method m1 = cls.getMethod("bar", int.class);
    Method m2 = cls.getMethod("bar", int.class, String.class);
    Method m3 = cls.getMethod("bar", int.class, String.class, String.class);

    m1.invoke(obj, <warning descr="Reflectively called method requires one argument">42, "abc"</warning>);
    m2.invoke(obj, 42, "abc");
    m3.invoke(obj, <warning descr="Reflectively called method requires 3 arguments">42, "abc"</warning>);

    m1.invoke(obj, <warning descr="Single-item array is expected">new Object[]{42, "abc"}</warning>);
    m2.invoke(obj, new Object[]{42, "abc"});
    m3.invoke(obj, <warning descr="3 array items are expected">new Object[]{42, "abc"}</warning>);

    m1.invoke<warning descr="Reflectively called method requires one argument">(obj)</warning>;
    m2.invoke<warning descr="Reflectively called method requires 2 arguments">(obj)</warning>;
    m3.invoke<warning descr="Reflectively called method requires 3 arguments">(obj)</warning>;

    cls.getMethod("str", String.class).invoke(null, new String[] {"abc"});
  }

  void arraySignatutre() throws Exception {
    Class<?> cls = Test.class;
    Object obj = new Test();

    Method m1 = cls.getMethod("bar", new Class[]{int.class});
    Method m2 = cls.getMethod("bar", new Class[]{int.class, String.class});
    Method m3 = cls.getMethod("bar", new Class[]{int.class, String.class, String.class});

    m1.invoke(obj, <warning descr="Reflectively called method requires one argument">42, "abc"</warning>);
    m2.invoke(obj, 42, "abc");
    m3.invoke(obj, <warning descr="Reflectively called method requires 3 arguments">42, "abc"</warning>);

    m1.invoke(obj, <warning descr="Single-item array is expected">new Object[]{42, "abc"}</warning>);
    m2.invoke(obj, new Object[]{42, "abc"});
    m3.invoke(obj, <warning descr="3 array items are expected">new Object[]{42, "abc"}</warning>);

    m1.invoke<warning descr="Reflectively called method requires one argument">(obj)</warning>;
    m2.invoke<warning descr="Reflectively called method requires 2 arguments">(obj)</warning>;
    m3.invoke<warning descr="Reflectively called method requires 3 arguments">(obj)</warning>;

    cls.getMethod("str", new Class[]{String.class}).invoke(null, new String[] {"abc"});
  }

  void manyArguments() throws Exception {
    Class<?> cls = M.class;
    Object obj = new M();

    Method m0 = cls.getMethod("m0");
    Method m1 = cls.getMethod("m1", int.class);
    Method m2 = cls.getMethod("m2", int.class, short.class);
    Method m3 = cls.getMethod("m3", int.class, short.class, long.class);
    Method m4 = cls.getMethod("m4", int.class, short.class, long.class, float.class);
    Method m5 = cls.getMethod("m5", int.class, short.class, long.class, float.class, double.class);

    m0.invoke(0, <warning descr="Reflectively called method requires no arguments">0</warning>);
    m1.invoke<warning descr="Reflectively called method requires one argument">("abc")</warning>;
    m2.invoke(0, <warning descr="Reflectively called method requires 2 arguments">"abc"</warning>);
    m3.invoke(0, <warning descr="Reflectively called method requires 3 arguments">0, "abc"</warning>);
    m4.invoke(0, <warning descr="Reflectively called method requires 4 arguments">0, 0, "abc"</warning>);
    m5.invoke(0, <warning descr="Reflectively called method requires 5 arguments">0, 0, 0, "abc"</warning>);

    Method m = cls.getMethod("m0", new Class[0]);
    m.invoke(obj, new Object[0]);
    m.invoke(obj, new Object[]{});
    m.invoke(obj, <warning descr="Empty array is expected">new Object[] {"abc"}</warning>);

    m = cls.getMethod("m0", new Class[]{});
    m.invoke(obj, new Object[]{});
  }

  static class Test {
    public void bar(int n) {}
    public void bar(int n, String s) {}
    public void bar(int n, String s, String t) {}

    public static void str(String s) {}
  }

  class M {
    public String m0() {return "";}
    public String m1(int a1) {return "";}
    public String m2(int a1, short a2) {return "";}
    public String m3(int a1, short a2, long a3) {return "";}
    public String m4(int a1, short a2, long a3, float a4) {return "";}
    public String m5(int a1, short a2, long a3, float a4, double a5) {return "";}
  }
}