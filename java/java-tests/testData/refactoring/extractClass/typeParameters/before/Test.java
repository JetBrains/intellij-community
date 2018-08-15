class Test<T, U> {

    public T foo(U u) { return null; }

    public void goo(T t, U u){
        t = foo(u)
    }
}