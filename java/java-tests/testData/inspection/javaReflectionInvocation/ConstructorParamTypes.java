import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.util.*;

class ConstructorParamTypes {
  void varargSignature() throws Exception {
    Class<?> cls = Test.class;

    Constructor c1 = cls.getConstructor(int.class, String.class);
    Constructor c2 = cls.getConstructor(String.class, Integer.class);
    Constructor c3 = cls.getConstructor(int[].class, List.class);

    c1.newInstance(42, "abc");
    c2.newInstance("abc", 42);
    c3.newInstance(new int[]{42, 23}, Arrays.asList("x", "y"));

    c1.newInstance(<warning descr="Argument is not assignable to 'int'">"42"</warning>, "abc");
    c1.newInstance(<warning descr="Argument is not assignable to 'int'">42.5</warning>, "abc");
    c2.newInstance(<warning descr="Argument is not assignable to 'java.lang.String'">42</warning>, 23);
    c2.newInstance("abc", <warning descr="Argument is not assignable to 'java.lang.Integer'">"def"</warning>);
    c3.newInstance(<warning descr="Argument is not assignable to 'int[]'">Arrays.asList("x", "y")</warning>, <warning descr="Argument is not assignable to 'java.util.List'">new int[]{42, 23}</warning>);
    c3.newInstance(<warning descr="Argument is not assignable to 'int[]'">42</warning>, <warning descr="Argument is not assignable to 'java.util.List'">"def"</warning>);


    final Object[] a1 = {new Integer(42), "abc"};
    final Object[] a2 = {"abc", Integer.valueOf(42)};
    final Object[] a3 = {new int[]{42, 23}, Arrays.asList("x", "y")};
    c1.newInstance(a1);
    c2.newInstance(a2);
    c3.newInstance(a3);

    c1.newInstance(new Object[] {new Integer(42), "abc"});
    c2.newInstance(new Object[] {"abc", Integer.valueOf(42)});
    c3.newInstance(new Object[] {new int[]{42, 23}, Arrays.asList("x", "y")});

    c1.newInstance(<warning descr="Array items have incompatible types">a2</warning>);
    c2.newInstance(<warning descr="Array items have incompatible types">a3</warning>);
    c3.newInstance(<warning descr="Array items have incompatible types">a1</warning>);

    c1.newInstance(new Object[]{<warning descr="Array item is not assignable to 'int'">"42"</warning>, "abc"});
    c2.newInstance(new Object[]{<warning descr="Array item is not assignable to 'java.lang.String'">42</warning>, 23});
    c3.newInstance(new Object[]{<warning descr="Array item is not assignable to 'int[]'">"x"</warning>, <warning descr="Array item is not assignable to 'java.util.List'">"y"</warning>});

    c1.newInstance(<warning descr="Argument is not assignable to 'int'">null</warning>, null);
    c2.newInstance(null, null);
    c3.newInstance(null, null);

    cls.getConstructor(String[].class).newInstance((Object)new String[] {"abc"});
    cls.getConstructor(String[].class).newInstance(new String[] {<warning descr="Array item is not assignable to 'java.lang.String[]'">"abc"</warning>});

    Constructor<?> c4 = cls.getConstructor(Cloneable.class);
    Constructor<?> c5 = cls.getConstructor(Serializable.class);

    c4.newInstance(new C());
    c5.newInstance(new S());

    c4.newInstance(<warning descr="Argument is not assignable to 'java.lang.Cloneable'">new S()</warning>);
    c5.newInstance(<warning descr="Argument is not assignable to 'java.io.Serializable'">new C()</warning>);
  }

  void arraySignature() throws Exception {
    Class<?> cls = Test.class;

    Constructor c1 = cls.getConstructor(new Class[]{int.class, String.class});
    Constructor c2 = cls.getConstructor(new Class[]{String.class, Integer.class});
    Constructor c3 = cls.getConstructor(new Class[]{int[].class, List.class});

    c1.newInstance(<warning descr="Argument is not assignable to 'int'">"42"</warning>, "abc");
    c1.newInstance(<warning descr="Argument is not assignable to 'int'">42.5</warning>, "abc");
    c2.newInstance(<warning descr="Argument is not assignable to 'java.lang.String'">42</warning>, 23);
    c2.newInstance("abc", <warning descr="Argument is not assignable to 'java.lang.Integer'">"def"</warning>);
    c3.newInstance(<warning descr="Argument is not assignable to 'int[]'">Arrays.asList("x", "y")</warning>, <warning descr="Argument is not assignable to 'java.util.List'">new int[]{42, 23}</warning>);
    c3.newInstance(<warning descr="Argument is not assignable to 'int[]'">42</warning>, <warning descr="Argument is not assignable to 'java.util.List'">"def"</warning>);

    final Object[] a1 = {new Integer(42), "abc"};
    final Object[] a2 = {"abc", Integer.valueOf(42)};
    final Object[] a3 = {new int[]{42, 23}, Arrays.asList("x", "y")};
    c1.newInstance(a1);
    c2.newInstance(a2);
    c3.newInstance(a3);

    c1.newInstance(new Object[] {new Integer(42), "abc"});
    c2.newInstance(new Object[] {"abc", Integer.valueOf(42)});
    c3.newInstance(new Object[] {new int[]{42, 23}, Arrays.asList("x", "y")});

    c1.newInstance(<warning descr="Array items have incompatible types">a2</warning>);
    c2.newInstance(<warning descr="Array items have incompatible types">a3</warning>);
    c3.newInstance(<warning descr="Array items have incompatible types">a1</warning>);

    c1.newInstance(new Object[]{<warning descr="Array item is not assignable to 'int'">"42"</warning>, "abc"});
    c2.newInstance(new Object[]{<warning descr="Array item is not assignable to 'java.lang.String'">42</warning>, 23});
    c3.newInstance(new Object[]{<warning descr="Array item is not assignable to 'int[]'">"x"</warning>, <warning descr="Array item is not assignable to 'java.util.List'">"y"</warning>});

    c1.newInstance(<warning descr="Argument is not assignable to 'int'">null</warning>, null);
    c2.newInstance(null, null);
    c3.newInstance(null, null);

    cls.getConstructor(new Class[]{String[].class}).newInstance((Object)new String[] {"abc"});
    cls.getConstructor(new Class[]{String[].class}).newInstance(new String[] {<warning descr="Array item is not assignable to 'java.lang.String[]'">"abc"</warning>});

    Constructor<?> c4 = cls.getConstructor(new Class[]{Cloneable.class});
    Constructor<?> c5 = cls.getConstructor(new Class[]{Serializable.class});

    c4.newInstance(new C());
    c5.newInstance(new S());

    c4.newInstance(<warning descr="Argument is not assignable to 'java.lang.Cloneable'">new S()</warning>);
    c5.newInstance(<warning descr="Argument is not assignable to 'java.io.Serializable'">new C()</warning>);
  }

  void manyArguments() throws Exception {
    Class<?> cls = M.class;

    Constructor m0 = cls.getConstructor();
    Constructor m1 = cls.getConstructor(int.class);
    Constructor m2 = cls.getConstructor(int.class, short.class);
    Constructor m3 = cls.getConstructor(int.class, short.class, long.class);
    Constructor m4 = cls.getConstructor(int.class, short.class, long.class, float.class);
    Constructor m5 = cls.getConstructor(int.class, short.class, long.class, float.class, double.class);

    Object[] a0 = {};
    Object[] a1 = {"abc"};
    Object[] a2 = {0, "abc"};
    Object[] a3 = {0, (short)0, "abc"};
    Object[] a4 = {0, (short)0, 0, "abc"};
    Object[] a5 = {0, (short)0, 0, 0.0f, "abc"};

    m0.newInstance(a0);
    m1.newInstance(<warning descr="Array item has incompatible type">a1</warning>);
    m2.newInstance(<warning descr="Array items have incompatible types">a2</warning>);
    m3.newInstance(<warning descr="Array items have incompatible types">a3</warning>);
    m4.newInstance(<warning descr="Array items have incompatible types">a4</warning>);
    m5.newInstance(<warning descr="Array items have incompatible types">a5</warning>);
  }

  void arrayArguments() throws Exception {
    Class<?> cls = Test.class;

    Constructor c1 = cls.getConstructor(Object.class);
    Constructor c2 = cls.getConstructor(Cloneable.class);
    Constructor c3 = cls.getConstructor(Serializable.class);

    c1.newInstance((Object) new String[0]);
    c2.newInstance((Object) new String[0]);
    c3.newInstance((Object) new String[0]);

    c1.newInstance((Object) new String[][]{ new String[0] });
    c2.newInstance((Object) new String[][]{ new String[0] });
    c3.newInstance((Object) new String[][]{ new String[0] });

    Constructor c4 = cls.getConstructor(Object[].class);
    Constructor c5 = cls.getConstructor(String[].class);

    c4.newInstance((Object) new String[0]);
    c4.newInstance((Object) new String[][]{ new String[0] });

    c5.newInstance((Object) new String[0]);
    c5.newInstance((Object) <warning descr="Argument is not assignable to 'java.lang.String[]'">new String[][]{ new String[0] }</warning>);
    c5.newInstance(<warning descr="Argument is not assignable to 'java.lang.String[]'">"abc"</warning>);
    c5.newInstance(<warning descr="Argument is not assignable to 'java.lang.String[]'">new Test(1, null)</warning>);
  }

  class Test {
    public Test(int n, String s) {}
    public Test(String s, Integer n) {}
    public Test(int[] n, List<String> s) {}
    public Test(String[] s) {}
    public Test(Object[] s) {}

    public Test(Object o) {}
    public Test(Cloneable c) {}
    public Test(Serializable s) {}
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