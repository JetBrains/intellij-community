class Foo {
    void test(Object obj) {
        if(!(obj instanceof String)) {
            obj = "foo";
        }
        obj.subst<caret>
    }
}
