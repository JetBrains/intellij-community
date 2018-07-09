class Foo {
    void test() {
        StringBuilder sb = new StringBuilder();
        <caret>sb.append("foo");
        sb.append("bar");
        sb.append("baz");
    }
}