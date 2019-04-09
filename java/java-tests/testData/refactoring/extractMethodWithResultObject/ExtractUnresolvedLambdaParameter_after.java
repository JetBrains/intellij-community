import java.util.function.Supplier;
class Test {

  private void a()
  {
    b((s) -> {
      System.out.println(newMethod(s).expressionResult);
    });
  }

    NewMethodResult newMethod(Object s) {
        return new NewMethodResult(s);
    }

    static class NewMethodResult {
        private boolean expressionResult;

        public NewMethodResult(boolean expressionResult) {
            this.expressionResult = expressionResult;
        }
    }

    void b(Supplier s) {}
}