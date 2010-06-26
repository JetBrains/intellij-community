// "Create Method 'get'" "true"
class Generic<T> {
    public T get() {
        <selection>return null;  //To change body of created methods use File | Settings | File Templates.<caret></selection>
    }
}

class WWW {
    <E> void foo (Generic<E> p) {
        E e = p.get();
    }
}