interface Pair<A extends String> {
    A get();
}

class B {
    <V extends String> void f(Pair<V> p) {
        V v = p.get();
    }

    {
        Pair<?> p = null;
        <caret>f(p);
    }
}


