class Test {
    interface I {
        Object foo();
    }

    static class Foo<X> { }

    {
        I i1 = <error descr="Unexpected wildcard">Foo<?></error>::new;
        I i2 = <error descr="Unexpected wildcard">Foo<? extends String></error>::new;
        I i3 = Foo<String>::new;

        I i4 = Foo<error descr="Generic array creation not allowed"><? extends String></error>[]::new;
        I i5 = Foo<error descr="Generic array creation not allowed"><String></error>[]::new;
    }

}