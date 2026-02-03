// "Create method 'get'" "true"
class Generic<T> {
    public T get() {
        <caret><selection>return null;</selection>
    }
}

class WWW {
    <E> void foo (Generic<E> p) {
        E e = p.get();
    }
}