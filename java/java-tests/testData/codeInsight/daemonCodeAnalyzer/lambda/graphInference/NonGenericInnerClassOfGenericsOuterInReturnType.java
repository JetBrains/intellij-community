class Outer<T> {
    private class Inner{}

    {
        Outer<String>.Inner inner = foo(foo(null));
    }

    private static <K> Outer<K>.Inner foo(Outer<K>.Inner u){
        return null;
    }
}
