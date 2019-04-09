
class Foo {
  void foo() {
    int x = 0;
      NewMethodResult x1 = newMethod();
  }

    NewMethodResult newMethod() {
        int x;
        x = 1;
        return new NewMethodResult((0 /* missing value */));
    }

    static class NewMethodResult {
        private int x;

        public NewMethodResult(int x) {
            this.x = x;
        }
    }
}