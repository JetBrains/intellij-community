// "Replace with method reference" "true-preview"
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