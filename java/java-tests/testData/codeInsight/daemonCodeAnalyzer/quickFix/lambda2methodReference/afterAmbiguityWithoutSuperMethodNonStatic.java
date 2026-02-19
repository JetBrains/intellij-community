// "Replace lambda with method reference" "true-preview"
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

  String foo(Foo foo) {
    return null;
  }

  public static void main(String[] args) {
    I i = Foo::foo;
  }
}
