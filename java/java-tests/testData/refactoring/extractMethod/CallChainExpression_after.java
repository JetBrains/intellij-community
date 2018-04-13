class CallChainExpression {
    String foo() {
        String s = newMethod();
        System.out.println(s);
        return s;
    }

    private String newMethod() {
        return A.a().b().c;
    }

    static class A {
        static A a() { return new A(); }
        A b() { return this; }
        String c = "";
    }
}