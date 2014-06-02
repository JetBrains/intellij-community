// "Replace with lambda" "false"
class Test {
  interface A {
    <K> void foo();
  }
  {
    A a = new <caret>A() {
      @Override
      public <K> void foo() {

      }
    };
  }
}