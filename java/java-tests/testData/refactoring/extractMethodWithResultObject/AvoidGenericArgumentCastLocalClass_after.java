class C {
    <K> void method() {
        class Local {
            void foo(K k) {
                System.out.println(k);
            }//ins and outs
//in: PsiParameter:k
//exit: SEQUENTIAL PsiMethod:foo

            public NewMethodResult newMethod(K k) {
                System.out.println(k);
                return new NewMethodResult();
            }

            public class NewMethodResult {
                public NewMethodResult() {
                }
            }

            void bar() {
                Object o = new Object();
                System.out.println(o);
            }
        }
    }
}