class Test {
  void foo(String[] ss) {
    System.out.println(ss[0]);
    System.out.println(ss[0]);
  }//ins and outs
//in: PsiParameter:ss
//exit: SEQUENTIAL PsiExpressionStatement

    public NewMethodResult newMethod(String[] ss) {
        return new NewMethodResult();
    }

    public class NewMethodResult {
    }
}