import java.util.function.Supplier;
class Test {

  private void a()
  {
    b(newMethod().expressionResult);
  }

    NewMethodResult newMethod() {
        return new NewMethodResult((s) -> {
          System.out.println(s);
        });
    }

    static class NewMethodResult {
        private Supplier expressionResult;

        public NewMethodResult(Supplier expressionResult) {
            this.expressionResult = expressionResult;
        }
    }

    void b(Supplier s) {}
}