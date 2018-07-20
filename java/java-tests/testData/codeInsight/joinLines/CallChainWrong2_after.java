class Foo {
    void test() {
        StringBuilder sb = new StringBuilder();
        if(sb.length() == 0) sb.append("bar");sb.append("baz");
    }
}