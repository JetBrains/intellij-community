
class A {
  private static final boolean ourOverrideFinalFields = false;

  public static String createShared(char[] chars) {

    if (ourOverrideFinalFields) {
      String s = new String();
      return s;
    }
    String s = newMethod().expressionResult;
    return new String(chars);
  }

    static NewMethodResult newMethod() {
        return new NewMethodResult(new String());
    }

    static class NewMethodResult {
        private String expressionResult;

        public NewMethodResult(String expressionResult) {
            this.expressionResult = expressionResult;
        }
    }


}