class Test {
  @interface FooBar {
    String x();
  }

  static void baz(FooBar annotation) {
    if (annotation.x() == null) {
      System.out.println("null");
    }
  }
}
