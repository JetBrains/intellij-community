class Test {
    void method(int i) {
        boolean isDirty = i == 0 || otherTests();
    }//ins and outs
//in: PsiParameter:i
//out: PsiBinaryExpression:i == 0
}