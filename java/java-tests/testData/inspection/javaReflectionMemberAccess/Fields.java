class Fields {
  void foo() throws Exception {
    Class ca = A.class;
    Class cb = B.class;

    ca.getField("a1");
    ca.getField(<warning descr="Field 'a2' is not public">"a2"</warning>);
    ca.getField(<warning descr="Field 'a3' is not public">"a3"</warning>);
    ca.getField(<warning descr="Field 'a4' is not public">"a4"</warning>);

    ca.getField("b1");
    ca.getField("b2");
    ca.getField("b3");
    ca.getField("b4");

    cb.getField("a1");
    cb.getField(<warning descr="Field 'a2' is not public">"a2"</warning>);
    cb.getField(<warning descr="Field 'a3' is not public">"a3"</warning>);
    cb.getField(<warning descr="Field 'a4' is not public">"a4"</warning>);

    cb.getField("b1");
    cb.getField(<warning descr="Field 'b2' is not public">"b2"</warning>);
    cb.getField(<warning descr="Field 'b3' is not public">"b3"</warning>);
    cb.getField(<warning descr="Field 'b4' is not public">"b4"</warning>);

    ca.getDeclaredField("a1");
    ca.getDeclaredField("a2");
    ca.getDeclaredField("a3");
    ca.getDeclaredField("a4");

    ca.getDeclaredField("b1");
    ca.getDeclaredField("b2");
    ca.getDeclaredField("b3");
    ca.getDeclaredField("b4");

    cb.getDeclaredField(<warning descr="Field 'a1' is not declared in class 'Fields.B'">"a1"</warning>);
    cb.getDeclaredField(<warning descr="Field 'a2' is not declared in class 'Fields.B'">"a2"</warning>);
    cb.getDeclaredField(<warning descr="Field 'a3' is not declared in class 'Fields.B'">"a3"</warning>);
    cb.getDeclaredField(<warning descr="Field 'a4' is not declared in class 'Fields.B'">"a4"</warning>);

    cb.getDeclaredField("b1");
    cb.getDeclaredField("b2");
    cb.getDeclaredField("b3");
    cb.getDeclaredField("b4");

    C.class.getField("c");
    C.class.getField(<warning descr="Cannot resolve field 'd'">"d"</warning>);
  }

  static class A {
    public int a1;
    protected int a2;
    int a3;
    private int a4;
  }

  static class B extends A {
    public int b1;
    protected int b2;
    int b3;
    private int b4;
  }

  static final class C extends A {
    public int c;
  }
}