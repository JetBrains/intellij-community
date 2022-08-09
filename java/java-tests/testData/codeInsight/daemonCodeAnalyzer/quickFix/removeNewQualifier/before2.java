// "Remove qualifier" "true-preview"

    static class A {
        static class B {
        }
        {
            new A() {
               void f() {





                     <caret>




               }
             }
            .new B();
        }
    }
