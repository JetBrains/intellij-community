class Foo {

    public String bar;
    private String baz;

    public static void set<caret>Bar(Foo foo, String bar) {
        foo.bar = bar;
        foo.baz = bar;
        foo.bar();
    }

    private void bar() {

    }
}