class Foo {
    void test() {
        StringBuilder sb = new StringBuilder();<caret>sb.append(sb);
        sb.append("bar");
        sb.append("baz");
    }
}