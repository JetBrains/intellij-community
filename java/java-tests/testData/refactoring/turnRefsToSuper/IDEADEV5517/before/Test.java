interface Int<T> {
    void method(T x);
}

class Sub implements Int<Xyz> {
    public void method(Xyz x) {
        x.inInt();
    }
}

interface XInt {
    void inInt();
}

class Xyz implements XInt {
    public void inInt() {
    }

    public void m1() {
    }
}