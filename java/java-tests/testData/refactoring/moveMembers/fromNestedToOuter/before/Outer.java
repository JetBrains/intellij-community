class Outer {
    static class Inner {
        final static String C0 = "";
        final static String C1 = "";
    }

    @A(Nested.C0)
    enum Enm {
        @A(Nested.C0)
        E0;

        @A(Nested.C1)
        void foo() {
        }
    }
}

@interface A {
    String value();
}