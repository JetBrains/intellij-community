class Outer {
    private void foo() {}

    class Inner extends Outer {
        void bar() {
            <caret>foo();
        }
    }

}