class Test {

  public static void main(String[] args){
    <selection>I i = ExceptionTest::foo;</selection>
  }

  class Ex extends Exception {}

  static void foo() throws Ex {}

  interface I {
    void f();
  }
}
