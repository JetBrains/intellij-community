// "Replace lambda with method reference" "true"
interface I {
  String foo(Foo i);
}

interface Bar {
  String foo();
}

class Foo implements Bar {
  public String foo() {
    return null;
  }

  String foo(int i) {
    return null;
  }

  static String foo(Foo foo) {
    return null;
  }

  public static void main(String[] args) {
    I i = Bar::foo;
  }
}
