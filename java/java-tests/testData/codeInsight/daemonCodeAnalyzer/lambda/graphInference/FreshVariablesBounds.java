
abstract class MyTest {
    interface A<S> {}
    interface B<K> {}
    abstract <_B, _P extends A<_B>> _P foo(B<_B> data);

    public void m(B b) {
        final A a = foo(b);
    }
}
