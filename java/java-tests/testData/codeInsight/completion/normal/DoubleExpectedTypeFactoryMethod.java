class Zoo2 {
    {
        foo(Key<caret>, true);
    }

    <T> void foo(Key<T> f, T b) { }
}

class Key<T> {
    static <T> Key<T> create() {} 
}