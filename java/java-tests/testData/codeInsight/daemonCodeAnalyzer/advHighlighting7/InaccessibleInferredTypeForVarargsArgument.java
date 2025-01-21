import java.util.Arrays;

class Test {
  {
    Arrays.asList<error descr="Formal varargs element type Outer.A is inaccessible here">(new Outer.B(), new Outer.C())</error>;
  }
}

class Outer {
  private static class A {}
  public static class B extends A {}
  public static class C extends A {}
}

class An {
  private class B {}
  public static void foo(B... x){}
}

class C {
  {
    An.foo<error descr="Formal varargs element type An.B is inaccessible here">()</error>;
  }
}
