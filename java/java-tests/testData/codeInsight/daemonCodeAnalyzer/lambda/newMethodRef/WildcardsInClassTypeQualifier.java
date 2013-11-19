class Test {
    interface I {
        Object foo();
    }

    static class Foo<X> { }

    {
        I i1 = <error descr="Unexpected wildcard">Foo<?></error>::new;
        I i2 = <error descr="Unexpected wildcard">Foo<? extends String></error>::new;
        I i3 = Foo<String>::new;

        I i4 = <error descr="Generic array creation">Foo<? extends String>[]</error>::new;
        I i5 = <error descr="Generic array creation">Foo<String>[]</error>::new;  
    }

}