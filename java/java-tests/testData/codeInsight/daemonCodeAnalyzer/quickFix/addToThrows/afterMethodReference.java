// "Add exception to method signature" "true"
class C {
  public static void main(String[] args){
    I i = C::foo;
  }

  class Ex extends Exception {}

  static void foo() throws Ex {}

  interface I {
    void f() throws Ex;
  }
}
