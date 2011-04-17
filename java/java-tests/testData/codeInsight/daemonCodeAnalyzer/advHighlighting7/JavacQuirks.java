class C {
  @interface TestAnnotation {
    int[] value();
  }

  @TestAnnotation({0, 1<warning descr="Trailing comma in annotation array initializer may cause compilation error in some Javac versions (e.g. JDK 5 and JDK 6).">,</warning>})
  void m() { }
}