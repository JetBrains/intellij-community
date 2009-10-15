public class Foo {

    {
        new Bar(<caret>) {}
    }

}

class Bar {
    Bar(Foo f) {}
}
