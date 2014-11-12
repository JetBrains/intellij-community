// "Replace lambda with method reference" "true"
interface I {
  String foo(Foo i);
}

class Foo {
  public String foo() {
    return null;
  }

  String foo(int i) {
    return null;
  }

  static String foo(Foo foo, boolean b) {
    return null;
  }

  public static void main(String[] args) {
    I i = (foo) -> foo.f<caret>oo();
  }
}
