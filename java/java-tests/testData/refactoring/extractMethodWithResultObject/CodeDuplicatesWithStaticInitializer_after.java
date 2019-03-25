class C {
   static C c;
   static {
        java.util.ArrayList<C> l = null;
        l.add(c);
    }

    void foo() {
      System.out.println(c);
    }//ins and outs
//exit: EXPRESSION PsiReferenceExpression:c

    public NewMethodResult newMethod() {
        return new NewMethodResult(c);
    }

    public class NewMethodResult {
        private C expressionResult;

        public NewMethodResult(C expressionResult) {
            this.expressionResult = expressionResult;
        }
    }
}