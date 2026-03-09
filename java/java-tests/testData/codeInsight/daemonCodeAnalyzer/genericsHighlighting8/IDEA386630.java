class A {
  <T extends Number> A() {}

  static <T extends Number> void create() {}

  static {
    A.<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>>create();
    new <<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>>A();
  }
}