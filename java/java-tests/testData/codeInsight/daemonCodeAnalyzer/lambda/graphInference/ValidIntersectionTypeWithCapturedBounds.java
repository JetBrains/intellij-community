
abstract class Bug {
    {
        D<?> _m = m();
    }

    abstract <J extends C<? extends String>> J m();
}

abstract class C<T extends String> { }
abstract class D<T extends String> extends C<T> { }
