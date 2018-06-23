class Foo {
    void test() {
        StringBuilder sb;
        sb = new StringBuilder().append("foo");<caret>
        sb.append("bar");
        sb.append("baz");
    }
}