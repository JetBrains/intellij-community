// "Remove qualifier" "true-preview"

    static class A {
        static class B {
        }
        {
            <caret>new B();
        }
    }
