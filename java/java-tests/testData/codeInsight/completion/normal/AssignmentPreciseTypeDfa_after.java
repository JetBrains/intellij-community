class Foo {
    void test(String s) {
        Object x;
        x = s;
        System.out.println(((String) x).substring());
    }
}
