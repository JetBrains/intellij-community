class Foo {
    void test() {
        StringBuilder sb = new StringBuilder();
        sb.append("foo").append("bar");<caret>
        sb.append("baz");
    }
}