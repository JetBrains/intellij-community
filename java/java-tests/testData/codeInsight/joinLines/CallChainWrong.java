class Foo {
    void test() {
        StringBuilder sb = new StringBuilder();
        <caret>sb.length();
        sb.append("bar");
        sb.append("baz");
    }
}