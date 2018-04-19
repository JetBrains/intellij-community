class Foo {
    void test() {
        StringBuilder sb;
        <caret>sb = new StringBuilder();
        sb.append("foo");
        sb.append("bar");
        sb.append("baz");
    }
}