class Test {
    void method(int i) {
        boolean isDirty = i == 0 || otherTests();
    }//ins and outs
//in: PsiParameter:i
//exit: EXPRESSION PsiBinaryExpression:i == 0

    public NewMethodResult newMethod(int i) {
        return new NewMethodResult();
    }

    public class NewMethodResult {
    }
}