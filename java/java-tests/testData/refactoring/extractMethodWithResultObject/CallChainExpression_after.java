class CallChainExpression {
    String foo() {
        String s = A.a().b().c;
        System.out.println(s);
        return s;
    }//ins and outs
//exit: EXPRESSION PsiReferenceExpression:A.a().b().c

    public NewMethodResult newMethod() {
        return new NewMethodResult();
    }

    public class NewMethodResult {
    }

    static class A {
        static A a() { return new A(); }
        A b() { return this; }
        String c = "";
    }
}