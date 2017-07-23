class Foo {
  String field;
  String field2;
  int hash = field.<warning descr="Method invocation 'hashCode' may produce 'java.lang.NullPointerException'">hashCode</warning>();

  Foo(String f2) {
    field2 = f2;
  }

  void someMethod() {
    if (<warning descr="Condition 'field == null' is always 'false'">field == null</warning>) {
      System.out.println("impossible");
    }
    if (field2 == null) {
      System.out.println("impossible");
    }
  }
}