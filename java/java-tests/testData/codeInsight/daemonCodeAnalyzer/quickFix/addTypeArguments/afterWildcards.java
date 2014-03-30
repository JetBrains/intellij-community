// "Add explicit type arguments" "true"
class X<A1, A2, R> { }

class D<R> {
    public <A1, A2> X<A1, A2, R> f() {
        return null;
    }
}

class Z {
    public void f() {
        g(new D<Void>().<String, Integer>f());
    }

    public void g(X<String, ? super Integer, Void> x) {

    }
}
