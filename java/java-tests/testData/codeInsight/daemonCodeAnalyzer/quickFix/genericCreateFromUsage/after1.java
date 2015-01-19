// "Create method 'get'" "true"
class W<T> {
    public T get(T s) {
        <selection>return null;</selection>
    }
}

class C {
    void foo () {
        W<String> w = new W<String>();
        String s = w.get("");
    }
}