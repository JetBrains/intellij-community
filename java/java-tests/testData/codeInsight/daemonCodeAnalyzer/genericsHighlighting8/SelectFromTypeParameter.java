import java.util.List;
interface Builder<T> {
    T build();
}

interface Test<D extends Test<D, X>, X> {
    static interface TestBuilder<D extends Test<D, X>, X> extends Builder<D> {}
}

interface Algorithm<T, B extends Builder<T>> {}

class SelectFromVariableType<X, T extends Test<T, X>>
        implements Algorithm<T,<error descr="Cannot select from a type parameter">T</error>.TestBuilder<T, X>> {
    List<T.TestBuilder<T, X>> b;
    T.TestBuilder<T, X> b1;
}
