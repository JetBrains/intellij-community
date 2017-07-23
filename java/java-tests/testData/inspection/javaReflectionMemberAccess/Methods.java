class Methods {
  void foo() throws Exception {
    Class ca = A.class;
    Class cb = B.class;

    ca.getMethod("a1", int.class);
    ca.getMethod(<warning descr="Method 'a2' is not public">"a2"</warning>, int.class);
    ca.getMethod(<warning descr="Method 'a3' is not public">"a3"</warning>, int.class);
    ca.getMethod(<warning descr="Method 'a4' is not public">"a4"</warning>, int.class);

    ca.getMethod("b1", int.class);
    ca.getMethod("b2", int.class);
    ca.getMethod("b3", int.class);
    ca.getMethod("b4", int.class);

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

    ca.getDeclaredMethod("b1", int.class);
    ca.getDeclaredMethod("b2", int.class);
    ca.getDeclaredMethod("b3", int.class);
    ca.getDeclaredMethod("b4", int.class);

    cb.getDeclaredMethod(<warning descr="Method 'a1' is not declared in class 'Methods.B'">"a1"</warning>, int.class);
    cb.getDeclaredMethod(<warning descr="Method 'a2' is not declared in class 'Methods.B'">"a2"</warning>, int.class);
    cb.getDeclaredMethod(<warning descr="Method 'a3' is not declared in class 'Methods.B'">"a3"</warning>, int.class);
    cb.getDeclaredMethod(<warning descr="Method 'a4' is not declared in class 'Methods.B'">"a4"</warning>, int.class);

    cb.getDeclaredMethod("b1", int.class);
    cb.getDeclaredMethod("b2", int.class);
    cb.getDeclaredMethod("b3", int.class);
    cb.getDeclaredMethod("b4", int.class);


    ca.getMethod("a5", String.class);
    ca.getMethod(<warning descr="Method 'a5' is not public">"a5"</warning>, String[].class);
    ca.getMethod("a5", String.class, String.class);
    ca.getMethod("a5", int.class);

    ca.getDeclaredMethod("a5", String.class);
    ca.getDeclaredMethod("a5", String[].class);
    ca.getDeclaredMethod("a5", String.class, String.class);
    ca.getDeclaredMethod("a5", int.class);

    cb.getMethod("a5", String.class);
    cb.getMethod(<warning descr="Method 'a5' is not public">"a5"</warning>, String[].class);
    cb.getMethod("a5", String.class, String.class);
    cb.getMethod("a5", int.class);

    cb.getDeclaredMethod(<warning descr="Method 'a5' is not declared in class 'Methods.B'">"a5"</warning>, String.class);
    cb.getDeclaredMethod(<warning descr="Method 'a5' is not declared in class 'Methods.B'">"a5"</warning>, String[].class);
    cb.getDeclaredMethod(<warning descr="Method 'a5' is not declared in class 'Methods.B'">"a5"</warning>, String.class, String.class);
    cb.getDeclaredMethod("a5", int.class);

    C.class.getMethod("c", int.class);
    C.class.getMethod(<warning descr="Cannot resolve method 'c' with specified argument types">"c"</warning>, boolean.class);
    C.class.getMethod(<warning descr="Cannot resolve method 'd'">"d"</warning>, int.class);
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
  }

  static final class C extends A {
    public int c(int n) {return n;}
  }
}