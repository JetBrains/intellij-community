// "Replace with lambda" "false"
class Test {
  {
    A a = new A<caret>("str") {
        @Override
        public void foo() {
        }
    };
  }
  static class A {
    A(String s) {
    }

    public void foo(){}
  }
}