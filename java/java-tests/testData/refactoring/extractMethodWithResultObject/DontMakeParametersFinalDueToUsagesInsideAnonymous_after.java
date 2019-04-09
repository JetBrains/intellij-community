class Box {
  private void test(String str1, String str2) {
    Data data = newMethod(str1, str2).expressionResult;
    System.out.println(data);
  }

    NewMethodResult newMethod(String str1, String str2) {
        return new NewMethodResult(new Data() {
          @Override
          public String getA() {
            return str1;
          }
          @Override
          public String getB() {
            return str2;
          }
        });
    }

    static class NewMethodResult {
        private Data expressionResult;

        public NewMethodResult(Data expressionResult) {
            this.expressionResult = expressionResult;
        }
    }

    static interface Data {
    String getA();
    String getB();
  }
}