class Test10 {
  void test() {
    new Object() {
      int get() {
        return 0;
      }
    };

    new Object() {
      int get() {
        return 0;
      }
    };
  }//ins and outs
//exit: SEQUENTIAL PsiExpressionStatement

    public NewMethodResult newMethod() {
        new Object() {
          int get() {
            return 0;
          }
        };
        return new NewMethodResult();
    }

    public class NewMethodResult {
        public NewMethodResult() {
        }
    }
}