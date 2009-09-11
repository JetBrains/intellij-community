interface Int<T> {
    void method(T x);
}

class Sub implements Int<Xint> {
    public void method(Xint x) {
        x.inInt();
    }
}

interface Xint {
    void inInt();
}

class Xyz implements Xint {
    public void inInt() {
    }

    public void m1() {
    }
}