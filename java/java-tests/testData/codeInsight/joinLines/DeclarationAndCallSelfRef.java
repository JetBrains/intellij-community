class Foo {
    void test() {
        <caret>StringBuilder sb = new StringBuilder();
        sb.append(sb);
        sb.append("bar");
        sb.append("baz");
    }
}