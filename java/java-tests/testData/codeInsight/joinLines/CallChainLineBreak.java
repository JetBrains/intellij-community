class Foo {
    void test() {
        StringBuilder sb = new StringBuilder();
        <caret>sb.append("long-long-long-long-long-long-long-long-long-long-long-long-long-long-long");
        sb.append("long-long-long-long-long-long-long-long-long-long-long-long-long-long-long");
    }
}