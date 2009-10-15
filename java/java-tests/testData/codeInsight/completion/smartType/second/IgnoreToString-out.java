class Foo {

    String foo();

    {
        Foo bar;
        String f = bar.foo();<caret>
    }

}
