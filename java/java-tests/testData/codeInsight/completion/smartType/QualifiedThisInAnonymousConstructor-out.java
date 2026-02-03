public class Foo {

    {
        new Bar(this)<caret> {}
    }

}

class Bar {
    Bar(Foo f) {}
}
