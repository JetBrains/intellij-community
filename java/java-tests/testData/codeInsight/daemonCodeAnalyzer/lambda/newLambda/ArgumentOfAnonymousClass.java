interface I<T> {
    void accept(T t);
}

class LamdbaTest<T> {

    public void f() {
        new A<T>(t -> g(t)) {};
    }

    private void g(T t) {
    }

    class A<T2> {
        public A(I<T> editor) {
        }
    }

}