class Foo {
    void test() {
        StringBuilder sb = new StringBuilder();
        sb.length();<caret>sb.append("bar");
        sb.append("baz");
    }
}