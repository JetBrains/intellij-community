class Foo {
    void test(String s) {
        Object x = s;
        System.out.println(x.subst<caret>);
    }
}
