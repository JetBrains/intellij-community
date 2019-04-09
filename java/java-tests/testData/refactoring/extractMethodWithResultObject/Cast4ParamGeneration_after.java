class Test {
  void foo(Object o) {
    if (o instanceof A) {
        NewMethodResult x = newMethod(o);
    }
  }

    NewMethodResult newMethod(Object o) {
        ((A)o).bar();
        return new NewMethodResult();
    }

    static class NewMethodResult {
        public NewMethodResult() {
        }
    }
}

class A {
  void bar(){}
}