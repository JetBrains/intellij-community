// "Add Exception to Method Signature" "true"
class C {
  public static void main(String[] args){
    I i = C::f<caret>oo;
  }

  class Ex extends Exception {}

  static void foo() throws Ex {}

  interface I {
    void f();
  }
}
