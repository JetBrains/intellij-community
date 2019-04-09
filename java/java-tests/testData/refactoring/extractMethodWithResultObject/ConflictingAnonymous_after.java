class Test {
  void foo() {
    new A() {
      void fe() {
          NewMethodResult x = newMethod();
      }

        NewMethodResult newMethod() {
            System.out.println("");
            return new NewMethodResult();
        }
    };
  }

    static class NewMethodResult {
        public NewMethodResult() {
        }
    }
}
class A {
  void newMethod(){}
}