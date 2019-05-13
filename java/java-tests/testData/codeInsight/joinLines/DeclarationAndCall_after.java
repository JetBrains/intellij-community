class Foo {
    void test() {
        StringBuilder sb = new StringBuilder().append("foo");<caret>
        sb.append("bar");
        sb.append("baz");
    }
}