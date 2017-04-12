import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.*;

class MethodParamTypes {
  void varargSignature() throws Exception {
    Class<?> cls = Test.class;
    Object obj = new Test();

    Method m1 = cls.getMethod("bar", int.class, String.class);
    Method m2 = cls.getMethod("bar", String.class, Integer.class);
    Method m3 = cls.getMethod("bar", int[].class, List.class);

    m1.invoke(obj, 42, "abc");
    m2.invoke(obj, "abc", 42);
    m3.invoke(obj, new int[]{42, 23}, Arrays.asList("x", "y"));

    m1.invoke(obj, <warning descr="Argument is not assignable to 'int'">"42"</warning>, "abc");
    m1.invoke(obj, <warning descr="Argument is not assignable to 'int'">42.5</warning>, "abc");
    m2.invoke(obj, <warning descr="Argument is not assignable to 'java.lang.String'">42</warning>, 23);
    m2.invoke(obj, "abc", <warning descr="Argument is not assignable to 'java.lang.Integer'">"def"</warning>);
    m3.invoke(obj, <warning descr="Argument is not assignable to 'int[]'">Arrays.asList("x", "y")</warning>, <warning descr="Argument is not assignable to 'java.util.List'">new int[]{42, 23}</warning>);
    m3.invoke(obj, <warning descr="Argument is not assignable to 'int[]'">42</warning>, <warning descr="Argument is not assignable to 'java.util.List'">"def"</warning>);


    final Object[] a1 = {new Integer(42), "abc"};
    final Object[] a2 = {"abc", Integer.valueOf(42)};
    final Object[] a3 = {new int[]{42, 23}, Arrays.asList("x", "y")};
    m1.invoke(obj, a1);
    m2.invoke(obj, a2);
    m3.invoke(obj, a3);

    m1.invoke(obj, new Object[] {new Integer(42), "abc"});
    m2.invoke(obj, new Object[] {"abc", Integer.valueOf(42)});
    m3.invoke(obj, new Object[] {new int[]{42, 23}, Arrays.asList("x", "y")});

    m1.invoke(obj, <warning descr="Array items have incompatible types">a2</warning>);
    m2.invoke(obj, <warning descr="Array items have incompatible types">a3</warning>);
    m3.invoke(obj, <warning descr="Array items have incompatible types">a1</warning>);

    m1.invoke(obj, new Object[]{<warning descr="Array item is not assignable to 'int'">"42"</warning>, "abc"});
    m2.invoke(obj, new Object[]{<warning descr="Array item is not assignable to 'java.lang.String'">42</warning>, 23});
    m3.invoke(obj, new Object[]{<warning descr="Array item is not assignable to 'int[]'">"x"</warning>, <warning descr="Array item is not assignable to 'java.util.List'">"y"</warning>});

    m1.invoke(obj, <warning descr="Argument is not assignable to 'int'">null</warning>, null);
    m2.invoke(obj, null, null);
    m3.invoke(obj, null, null);

    cls.getMethod("str", String[].class).invoke(null, (Object)new String[] {"abc"});
    cls.getMethod("str", String[].class).invoke(null, new String[] {<warning descr="Array item is not assignable to 'java.lang.String[]'">"abc"</warning>});

    Method m4 = cls.getMethod("obj", Cloneable.class);
    Method m5 = cls.getMethod("obj", Serializable.class);

    m4.invoke(obj, new C());
    m5.invoke(obj, new S());

    m4.invoke(obj, <warning descr="Argument is not assignable to 'java.lang.Cloneable'">new S()</warning>);
    m5.invoke(obj, <warning descr="Argument is not assignable to 'java.io.Serializable'">new C()</warning>);
  }

  void arraySignature() throws Exception {
    Class<?> cls = Test.class;
    Object obj = new Test();

    Method m1 = cls.getMethod("bar", new Class[]{int.class, String.class});
    Method m2 = cls.getMethod("bar", new Class[]{String.class, Integer.class});
    Method m3 = cls.getMethod("bar", new Class[]{int[].class, List.class});

    m1.invoke(obj, <warning descr="Argument is not assignable to 'int'">"42"</warning>, "abc");
    m1.invoke(obj, <warning descr="Argument is not assignable to 'int'">42.5</warning>, "abc");
    m2.invoke(obj, <warning descr="Argument is not assignable to 'java.lang.String'">42</warning>, 23);
    m2.invoke(obj, "abc", <warning descr="Argument is not assignable to 'java.lang.Integer'">"def"</warning>);
    m3.invoke(obj, <warning descr="Argument is not assignable to 'int[]'">Arrays.asList("x", "y")</warning>, <warning descr="Argument is not assignable to 'java.util.List'">new int[]{42, 23}</warning>);
    m3.invoke(obj, <warning descr="Argument is not assignable to 'int[]'">42</warning>, <warning descr="Argument is not assignable to 'java.util.List'">"def"</warning>);

    final Object[] a1 = {new Integer(42), "abc"};
    final Object[] a2 = {"abc", Integer.valueOf(42)};
    final Object[] a3 = {new int[]{42, 23}, Arrays.asList("x", "y")};
    m1.invoke(obj, a1);
    m2.invoke(obj, a2);
    m3.invoke(obj, a3);

    m1.invoke(obj, new Object[] {new Integer(42), "abc"});
    m2.invoke(obj, new Object[] {"abc", Integer.valueOf(42)});
    m3.invoke(obj, new Object[] {new int[]{42, 23}, Arrays.asList("x", "y")});

    m1.invoke(obj, <warning descr="Array items have incompatible types">a2</warning>);
    m2.invoke(obj, <warning descr="Array items have incompatible types">a3</warning>);
    m3.invoke(obj, <warning descr="Array items have incompatible types">a1</warning>);

    m1.invoke(obj, new Object[]{<warning descr="Array item is not assignable to 'int'">"42"</warning>, "abc"});
    m2.invoke(obj, new Object[]{<warning descr="Array item is not assignable to 'java.lang.String'">42</warning>, 23});
    m3.invoke(obj, new Object[]{<warning descr="Array item is not assignable to 'int[]'">"x"</warning>, <warning descr="Array item is not assignable to 'java.util.List'">"y"</warning>});

    m1.invoke(obj, <warning descr="Argument is not assignable to 'int'">null</warning>, null);
    m2.invoke(obj, null, null);
    m3.invoke(obj, null, null);

    cls.getMethod("str", new Class[]{String[].class}).invoke(null, (Object)new String[] {"abc"});
    cls.getMethod("str", new Class[]{String[].class}).invoke(null, new String[] {<warning descr="Array item is not assignable to 'java.lang.String[]'">"abc"</warning>});

    Method m4 = cls.getMethod("obj", new Class[]{Cloneable.class});
    Method m5 = cls.getMethod("obj", new Class[]{Serializable.class});

    m4.invoke(obj, new C());
    m5.invoke(obj, new S());

    m4.invoke(obj, <warning descr="Argument is not assignable to 'java.lang.Cloneable'">new S()</warning>);
    m5.invoke(obj, <warning descr="Argument is not assignable to 'java.io.Serializable'">new C()</warning>);
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

    Object[] a0 = {};
    Object[] a1 = {"abc"};
    Object[] a2 = {0, "abc"};
    Object[] a3 = {0, (short)0, "abc"};
    Object[] a4 = {0, (short)0, 0, "abc"};
    Object[] a5 = {0, (short)0, 0, 0.0f, "abc"};

    m0.invoke(obj, a0);
    m1.invoke(obj, <warning descr="Array item has incompatible type">a1</warning>);
    m2.invoke(obj, <warning descr="Array items have incompatible types">a2</warning>);
    m3.invoke(obj, <warning descr="Array items have incompatible types">a3</warning>);
    m4.invoke(obj, <warning descr="Array items have incompatible types">a4</warning>);
    m5.invoke(obj, <warning descr="Array items have incompatible types">a5</warning>);
  }

  void arrayArguments() throws Exception {
    Class<?> cls = Test.class;
    Object obj = new Test();

    Method m1 = cls.getMethod("obj", Object.class);
    Method m2 = cls.getMethod("obj", Cloneable.class);
    Method m3 = cls.getMethod("obj", Serializable.class);

    m1.invoke(obj, (Object) new String[0]);
    m2.invoke(obj, (Object) new String[0]);
    m3.invoke(obj, (Object) new String[0]);

    m1.invoke(obj, (Object) new String[][]{ new String[0] });
    m2.invoke(obj, (Object) new String[][]{ new String[0] });
    m3.invoke(obj, (Object) new String[][]{ new String[0] });

    Method m4 = cls.getMethod("array", Object[].class);
    Method m5 = cls.getMethod("array", String[].class);

    m4.invoke(null, (Object) new String[0]);
    m4.invoke(null, (Object) new String[][]{ new String[0] });

    m5.invoke(null, (Object) new String[0]);
    m5.invoke(null, (Object) <warning descr="Argument is not assignable to 'java.lang.String[]'">new String[][]{ new String[0] }</warning>);
    m5.invoke(null, <warning descr="Argument is not assignable to 'java.lang.String[]'">"abc"</warning>);
    m5.invoke(null, <warning descr="Argument is not assignable to 'java.lang.String[]'">obj</warning>);
  }

  static class Test {
    public void bar(int n, String s) {}
    public void bar(String s, Integer n) {}
    public void bar(int[] n, List<String> s) {}

    public static void str(String[] s) {}

    public void obj(Object o) {}
    public void obj(Cloneable c) {}
    public void obj(Serializable s) {}

    public static void array(Object[] a) {}
    public static void array(String[] a) {}
  }

  class M {
    public String m1(int a1) {return "";}
    public String m2(int a1, short a2) {return "";}
    public String m3(int a1, short a2, long a3) {return "";}
    public String m4(int a1, short a2, long a3, float a4) {return "";}
    public String m5(int a1, short a2, long a3, float a4, double a5) {return "";}
  }

  static class S implements Serializable {
  }

  static class C implements Cloneable {
  }
}