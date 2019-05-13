// "Create method 'get'" "true"
class Generic<T> {
}

class WWW {
    <E> void foo (Generic<E> p) {
        E e = p.g<caret>et();
    }
}