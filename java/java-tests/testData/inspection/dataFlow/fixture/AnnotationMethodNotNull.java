class Test {
  @interface FooBar {
    String x();
  }

  static void baz(FooBar annotation) {
    if (<warning descr="Condition 'annotation.x() == null' is always 'false'">annotation.x() == null</warning>) {
      System.out.println("null");
    }
  }
}
