interface Int<T> {
    void method(T x);
}

class Sub implements Int<XInt> {
    public void method(XInt x) {
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