// "Replace with method reference" "true"
class Main {

  interface A {
    <K> void foo();
  }

  static void mm(){}

  {
    A a = new A<caret>() {
      @Override
      public <K> void foo() {
        mm();
      }
    };
  }
}