class C {
    <K> void method() {
        class Local {
            void foo(K k) {
                <selection>System.out.println(k);</selection>
            }
            void bar() {
                Object o = new Object();
                System.out.println(o);
            }
        }
    }
}