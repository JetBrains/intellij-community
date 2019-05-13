class Foo {
    void test() {
        <caret>StringBuilder sb = new StringBuilder();
        sb.append("foo");
        sb.append("bar");
        sb.append("baz");
    }
}