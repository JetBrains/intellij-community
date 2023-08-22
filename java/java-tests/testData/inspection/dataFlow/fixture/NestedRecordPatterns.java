class Hello {
  record X(X x) {}

  static void foo(Object obj) {
    if (!(obj instanceof X)) {
      return;
    }

    System.out.println(obj instanceof X(X(X(X(X x)))));
  }

  public static void main(String[] args) {
    foo(new X(new X(<warning descr="Passing 'null' argument to non-annotated parameter">null</warning>)));
  }
}
