class CallChainExpression {
    String foo() {
        String s = <selection>A.a().b().c</selection>;
        System.out.println(s);
        return s;
    }

    static class A {
        static A a() { return new A(); }
        A b() { return this; }
        String c = "";
    }
}