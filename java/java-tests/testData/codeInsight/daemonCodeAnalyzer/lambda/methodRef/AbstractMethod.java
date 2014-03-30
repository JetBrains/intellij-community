class Test {
    interface I {
        int _();
    }

    static abstract class Parent {
        abstract int foo() ;
    }

    static abstract class Child extends Parent {
        abstract int foo() ;
        void test() {
            I s = <error descr="Abstract method 'foo' cannot be accessed directly">super::foo</error>;
            I s1 = this::foo;

            Parent sup = null;
            I s2 = sup::foo;
        }
    }
}