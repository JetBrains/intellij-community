class C {
   static C c;
   static {
        java.util.ArrayList<C> l = null;
        l.add(c);
    }

    void foo() {
      System.out.println(newMethod().expressionResult);
    }

    NewMethodResult newMethod() {
        return new NewMethodResult(c);
    }

    static class NewMethodResult {
        private C expressionResult;

        public NewMethodResult(C expressionResult) {
            this.expressionResult = expressionResult;
        }
    }
}