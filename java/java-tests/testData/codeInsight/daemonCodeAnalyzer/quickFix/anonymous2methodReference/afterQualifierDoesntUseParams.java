// "Replace with method reference" "true"
interface Foo<A, B> {
    B f(A a);
}

interface DeeBee<A> {
    A run(Void c) throws SQLException;

    <B> DeeBee<B> bind(final Foo<A, DeeBee<B>> f) default {
        return f.f(null)::run;
    }
}