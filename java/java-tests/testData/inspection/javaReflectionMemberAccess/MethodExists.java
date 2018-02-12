class MethodExists {
  void foo() throws Exception {
    Class ca = A.class;
    Class cb = B.class;

    ca.getMethod("a1", int.class);
    ca.getMethod(<warning descr="Method 'a2' is not public">"a2"</warning>, int.class);
    ca.getMethod(<warning descr="Method 'a3' is not public">"a3"</warning>, int.class);
    ca.getMethod(<warning descr="Method 'a4' is not public">"a4"</warning>, int.class);

    ca.getMethod(<warning descr="Cannot resolve method 'b1'">"b1"</warning>, int.class);
    ca.getMethod(<warning descr="Cannot resolve method 'b2'">"b2"</warning>, int.class);
    ca.getMethod(<warning descr="Cannot resolve method 'b3'">"b3"</warning>, int.class);
    ca.getMethod(<warning descr="Cannot resolve method 'b4'">"b4"</warning>, int.class);

    cb.getMethod("a1", int.class);
    cb.getMethod(<warning descr="Method 'a2' is not public">"a2"</warning>, int.class);
    cb.getMethod(<warning descr="Method 'a3' is not public">"a3"</warning>, int.class);
    cb.getMethod(<warning descr="Method 'a4' is not public">"a4"</warning>, int.class);

    cb.getMethod("b1", int.class);
    cb.getMethod(<warning descr="Method 'b2' is not public">"b2"</warning>, int.class);
    cb.getMethod(<warning descr="Method 'b3' is not public">"b3"</warning>, int.class);
    cb.getMethod(<warning descr="Method 'b4' is not public">"b4"</warning>, int.class);


    ca.getDeclaredMethod("a1", int.class);
    ca.getDeclaredMethod("a2", int.class);
    ca.getDeclaredMethod("a3", int.class);
    ca.getDeclaredMethod("a4", int.class);

    ca.getDeclaredMethod(<warning descr="Cannot resolve method 'b1'">"b1"</warning>, int.class);
    ca.getDeclaredMethod(<warning descr="Cannot resolve method 'b2'">"b2"</warning>, int.class);
    ca.getDeclaredMethod(<warning descr="Cannot resolve method 'b3'">"b3"</warning>, int.class);
    ca.getDeclaredMethod(<warning descr="Cannot resolve method 'b4'">"b4"</warning>, int.class);

    cb.getDeclaredMethod(<warning descr="Method 'a1' is not declared in class 'MethodExists.B'">"a1"</warning>, int.class);
    cb.getDeclaredMethod(<warning descr="Method 'a2' is not declared in class 'MethodExists.B'">"a2"</warning>, int.class);
    cb.getDeclaredMethod(<warning descr="Method 'a3' is not declared in class 'MethodExists.B'">"a3"</warning>, int.class);
    cb.getDeclaredMethod(<warning descr="Method 'a4' is not declared in class 'MethodExists.B'">"a4"</warning>, int.class);

    cb.getDeclaredMethod("b1", int.class);
    cb.getDeclaredMethod("b2", int.class);
    cb.getDeclaredMethod("b3", int.class);
    cb.getDeclaredMethod("b4", int.class);


    ca.getMethod("a5", String.class);
    ca.getMethod(<warning descr="Method 'a5' is not public">"a5"</warning>, String[].class);
    ca.getMethod("a5", String.class, String.class);
    ca.getMethod(<warning descr="Cannot resolve method 'a5' with specified argument types">"a5"</warning>, int.class);

    ca.getDeclaredMethod("a5", String.class);
    ca.getDeclaredMethod("a5", String[].class);
    ca.getDeclaredMethod("a5", String.class, String.class);
    ca.getDeclaredMethod(<warning descr="Cannot resolve method 'a5' with specified argument types">"a5"</warning>, int.class);

    cb.getMethod("a5", String.class);
    cb.getMethod(<warning descr="Method 'a5' is not public">"a5"</warning>, String[].class);
    cb.getMethod("a5", String.class, String.class);
    cb.getMethod(<warning descr="Cannot resolve method 'a5' with specified argument types">"a5"</warning>, int.class);

    cb.getDeclaredMethod(<warning descr="Method 'a5' is not declared in class 'MethodExists.B'">"a5"</warning>, String.class);
    cb.getDeclaredMethod(<warning descr="Method 'a5' is not declared in class 'MethodExists.B'">"a5"</warning>, String[].class);
    cb.getDeclaredMethod(<warning descr="Method 'a5' is not declared in class 'MethodExists.B'">"a5"</warning>, String.class, String.class);
    cb.getDeclaredMethod(<warning descr="Cannot resolve method 'a5' with specified argument types">"a5"</warning>, int.class);

    C.class.getMethod("c", int.class);
    C.class.getMethod(<warning descr="Cannot resolve method 'c' with specified argument types">"c"</warning>, boolean.class);
    C.class.getMethod(<warning descr="Cannot resolve method 'd'">"d"</warning>, int.class);

    A.class.getMethod("a1", Integer.TYPE);
    A.class.getMethod(<warning descr="Method 'a2' is not public">"a2"</warning>, Integer.TYPE);
    A.class.getMethod(<warning descr="Cannot resolve method 'a1' with specified argument types">"a1"</warning>, Boolean.TYPE);

    B.class.getMethod("noArg");
    B.class.getMethod("noArg", new Class[0]);
    B.class.getMethod("noArg", null);

    A.class.getMethod(<warning descr="Cannot resolve method 'noArg'">"noArg"</warning>);
    A.class.getMethod(<warning descr="Cannot resolve method 'noArg'">"noArg"</warning>, new Class[0]);
    A.class.getMethod(<warning descr="Cannot resolve method 'noArg'">"noArg"</warning>, null);

    B.class.getDeclaredMethod("noArg");
    B.class.getDeclaredMethod("noArg", new Class[0]);
    B.class.getDeclaredMethod("noArg", null);

    A.class.getDeclaredMethod(<warning descr="Cannot resolve method 'noArg'">"noArg"</warning>);
    A.class.getDeclaredMethod(<warning descr="Cannot resolve method 'noArg'">"noArg"</warning>, new Class[0]);
    A.class.getDeclaredMethod(<warning descr="Cannot resolve method 'noArg'">"noArg"</warning>, null);

  }

  static class A {
    public int a1(int n) {return n;}
    protected int a2(int n) {return n;}
    int a3(int n) {return n;}
    private int a4(int n) {return n;}

    public String a5(String s) {return s;}
    public String a5(String s, String t) {return s;}
    protected String a5(String[] s) {return s[0];}
  }

  static class B extends A {
    public int b1(int n) {return n;}
    protected int b2(int n) {return n;}
    int b3(int n) {return n;}
    private int b4(int n) {return n;}

    public String b5(String s) {return s;}
    public String b5(String s, String t) {return s;}
    protected String b5(String[] s) {return s[0];}

    public String noArg() {return "";}
  }

  static final class C extends A {
    public int c(int n) {return n;}
  }
}