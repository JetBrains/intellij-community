class Test {
    void foo() {
        bar(<selection>String.valueOf(1)</selection>);
        baz(String.valueOf(1));
    }

    private void bar(String s) {
    }

    private void baz(String... s) {
    }
}