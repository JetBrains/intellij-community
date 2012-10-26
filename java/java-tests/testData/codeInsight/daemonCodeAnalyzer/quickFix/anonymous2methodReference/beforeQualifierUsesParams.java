// "Replace with method reference" "false"
interface Foo<A, B> {
    B f(A a);
}

interface DeeBee<A> {
    A run(Void c) throws SQLException;

    <B> DeeBee<B> bind(final Foo<A, DeeBee<B>> f) default {
        return new Dee<caret>Bee<B> () {
            public B run(final Void c) throws SQLException {
               return f.f(DeeBee.this.run(c)).run(c);
            }
        };
    }
}