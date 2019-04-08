import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class X {
  @NotNull
  public X fun1(int x) {
    return this;
  }

  public X fun2(@Nullable @SuppressWarnings("unused") String b) {

      NewMethodResult x1 = newMethod(b);
      if (x1.exitKey == 1) return x1.returnResult;


      int x = 0;
    return null;
  }//ins and outs
//in: PsiParameter:b
//exit: RETURN PsiMethod:fun2<-PsiMethodCallExpression:fun1(x)
//exit: SEQUENTIAL PsiIfStatement

    NewMethodResult newMethod(String b) {
        if (b != null) {
          int x = 1;
            return new NewMethodResult((1 /* exit key */), fun1(x));
        }
        return new NewMethodResult((-1 /* exit key */), (null /* missing value */));
    }

    static class NewMethodResult {
        private int exitKey;
        private X returnResult;

        public NewMethodResult(int exitKey, X returnResult) {
            this.exitKey = exitKey;
            this.returnResult = returnResult;
        }
    }
}
