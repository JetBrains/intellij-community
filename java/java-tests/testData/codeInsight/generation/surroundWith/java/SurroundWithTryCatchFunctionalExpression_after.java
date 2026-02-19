class Test {

  public static void main(String[] args){
      try {
          I i = ExceptionTest::foo;
      } catch (Exception e) {
          throw new RuntimeException(e);
      }
  }

  class Ex extends Exception {}

  static void foo() throws Ex {}

  interface I {
    void f();
  }
}
