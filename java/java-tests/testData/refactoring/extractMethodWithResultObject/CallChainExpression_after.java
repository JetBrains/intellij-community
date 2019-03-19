class CallChainExpression {
    String foo() {
        String s = A.a().b().c;
        System.out.println(s);
        return s;
    }//ins and outs
//out: EXPRESSION PsiReferenceExpression:A.a().b().c

    static class A {
        static A a() { return new A(); }
        A b() { return this; }
        String c = "";
    }
}