public class InnerClass {
    void foo() {}

    class Inner {
        void foo(int i) {}

        void bar() {
            <caret>foo();
        }
    }

}