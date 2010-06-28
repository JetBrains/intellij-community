// "Remove qualifier" "true"

    static class A {
        static class B {
        }
        {
            <caret>new B();
        }
    }
