class Outer {
    private void foo() {}

    static class Inner extends Outer {
        void bar() {
            <ref>foo();
        }
    }

}