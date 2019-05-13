class A {

    class B {

        private void foo() {}
    }
    class C extends B {

        class D {

            void bar() {
                C.this.<error descr="'foo()' has private access in 'A.B'">foo</error>();
            }
        }
    }
}