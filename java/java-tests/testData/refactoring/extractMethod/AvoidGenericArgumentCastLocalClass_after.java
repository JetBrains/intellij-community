class C {
    <K> void method() {
        class Local {
            void foo(K k) {
                newMethod(k);
            }

            private void newMethod(K k) {
                System.out.println(k);
            }

            void bar() {
                Object o = new Object();
                newMethod(o);
            }
        }
    }
}