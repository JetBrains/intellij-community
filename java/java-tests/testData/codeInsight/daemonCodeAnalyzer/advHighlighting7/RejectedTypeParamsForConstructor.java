class Foo {
    {
        Foo f = new Foo<error descr="Type 'Foo' does not have type parameters"><String></error>();
        Foo.<String>foo();
    }

    static void foo() {}
}

class Bar {
    <T> Bar() {}
    {
      Bar b = new <String>Bar();
    }
}

class Baz {
    Baz() {}
    {
      Baz b = new <String>Baz();
    }
}
