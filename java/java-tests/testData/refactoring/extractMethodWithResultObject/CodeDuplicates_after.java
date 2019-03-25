class C {
    {
        int i;

        System.out.println(i);
        System.out.println(128);
    }//ins and outs
//in: PsiLocalVariable:i
//exit: SEQUENTIAL PsiExpressionStatement

    public NewMethodResult newMethod(int i) {
        System.out.println(i);
        return new NewMethodResult();
    }

    public class NewMethodResult {
        public NewMethodResult() {
        }
    }
}