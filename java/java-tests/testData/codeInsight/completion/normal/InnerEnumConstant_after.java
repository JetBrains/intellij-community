class Foo {
    {
        Bar b = Bar.FOOOOOOO<caret>
    }

    enum Bar {
        FOOOOOOO
    }
}