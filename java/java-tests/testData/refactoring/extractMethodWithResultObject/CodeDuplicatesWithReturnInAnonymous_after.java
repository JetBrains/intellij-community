class Test10 {
  void test() {
      NewMethodResult x = newMethod();

      new Object() {
      int get() {
        return 0;
      }
    };
  }

    NewMethodResult newMethod() {
        new Object() {
          int get() {
            return 0;
          }
        };
        return new NewMethodResult();
    }

    static class NewMethodResult {
        public NewMethodResult() {
        }
    }
}