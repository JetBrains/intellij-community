class Foo {
  String field;
  String field2 = field.<warning descr="Method invocation 'substring' may produce 'java.lang.NullPointerException'">substring</warning>(1);
  int field3 = field2.length();
  Runnable r = new Runnable() {
    public void run() {
      System.out.println(field.hashCode());
    }
  };

  Foo() {
    field = "x";
  }
}