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

    c1.newInstance(<warning descr="Array item 0 is not assignable to 'int'"><warning descr="Array item 1 is not assignable to 'java.lang.String'">a2</warning></warning>);
    c2.newInstance(<warning descr="Array item 0 is not assignable to 'java.lang.String'"><warning descr="Array item 1 is not assignable to 'java.lang.Integer'">a3</warning></warning>);
    c3.newInstance(<warning descr="Array item 0 is not assignable to 'int[]'"><warning descr="Array item 1 is not assignable to 'java.util.List'">a1</warning></warning>);

    c1.newInstance(new Object[]{<warning descr="Array item is not assignable to 'int'">"42"</warning>, "abc"});
    c2.newInstance(new Object[]{<warning descr="Array item is not assignable to 'java.lang.String'">42</warning>, 23});
    c3.newInstance(new Object[]{<warning descr="Array item is not assignable to 'int[]'">"x"</warning>, <warning descr="Array item is not assignable to 'java.util.List'">"y"</warning>});

    c1.newInstance(<warning descr="Argument is not assignable to 'int'">null</warning>, null);
    c2.newInstance(null, null);
    c3.newInstance(null, null);

    cls.getConstructor(String[].class).newInstance((Object)new String[] {"abc"});
    cls.getConstructor(String[].class).newInstance(new String[] {<warning descr="Array item is not assignable to 'java.lang.String[]'">"abc"</warning>});
  }

  void arraySignatutre() throws Exception {
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

    c1.newInstance(<warning descr="Array item 0 is not assignable to 'int'"><warning descr="Array item 1 is not assignable to 'java.lang.String'">a2</warning></warning>);
    c2.newInstance(<warning descr="Array item 0 is not assignable to 'java.lang.String'"><warning descr="Array item 1 is not assignable to 'java.lang.Integer'">a3</warning></warning>);
    c3.newInstance(<warning descr="Array item 0 is not assignable to 'int[]'"><warning descr="Array item 1 is not assignable to 'java.util.List'">a1</warning></warning>);

    c1.newInstance(new Object[]{<warning descr="Array item is not assignable to 'int'">"42"</warning>, "abc"});
    c2.newInstance(new Object[]{<warning descr="Array item is not assignable to 'java.lang.String'">42</warning>, 23});
    c3.newInstance(new Object[]{<warning descr="Array item is not assignable to 'int[]'">"x"</warning>, <warning descr="Array item is not assignable to 'java.util.List'">"y"</warning>});

    c1.newInstance(<warning descr="Argument is not assignable to 'int'">null</warning>, null);
    c2.newInstance(null, null);
    c3.newInstance(null, null);

    cls.getConstructor(new Class[]{String[].class}).newInstance((Object)new String[] {"abc"});
    cls.getConstructor(new Class[]{String[].class}).newInstance(new String[] {<warning descr="Array item is not assignable to 'java.lang.String[]'">"abc"</warning>});
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
    m1.newInstance(<warning descr="Array item 0 is not assignable to 'int'">a1</warning>);
    m2.newInstance(<warning descr="Array item 1 is not assignable to 'short'">a2</warning>);
    m3.newInstance(<warning descr="Array item 2 is not assignable to 'long'">a3</warning>);
    m4.newInstance(<warning descr="Array item 3 is not assignable to 'float'">a4</warning>);
    m5.newInstance(<warning descr="Array item 4 is not assignable to 'double'">a5</warning>);
  }

  class Test {
    public Test(int n, String s) {}
    public Test(String s, Integer n) {}
    public Test(int[] n, List<String> s) {}
    public Test(String[] s) {}
  }

  class M {
    public String m1(int a1) {return "";}
    public String m2(int a1, short a2) {return "";}
    public String m3(int a1, short a2, long a3) {return "";}
    public String m4(int a1, short a2, long a3, float a4) {return "";}
    public String m5(int a1, short a2, long a3, float a4, double a5) {return "";}
  }
}