class C {
    <K> void method() {
        class Local {
            void foo(K k) {
                System.out.println(k);
            }//ins and outs
//in: PsiParameter:k
//exit: SEQUENTIAL PsiMethod:foo

            void bar() {
                Object o = new Object();
                System.out.println(o);
            }
        }
    }
}