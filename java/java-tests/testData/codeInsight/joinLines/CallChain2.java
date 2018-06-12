class Foo {
    void test() {
        StringBuilder sb = new StringBuilder();
        <caret>sb.append("foo").append("bar");
        sb.append("baz");
    }
}