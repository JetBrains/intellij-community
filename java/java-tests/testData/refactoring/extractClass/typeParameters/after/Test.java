class Test<T, U> {

    private final Extracted<T, U> extracted = new Extracted<T, U>();

    public T foo(U u) {
        return extracted.foo(u);
    }

    public void goo(T t, U u){
        t = extracted.foo(u)
    }
}