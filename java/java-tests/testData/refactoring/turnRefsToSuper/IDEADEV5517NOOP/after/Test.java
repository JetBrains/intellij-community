interface Int<T> {
    void method(T x);
    void method1(T x);
}

class Sub implements Int<Xyz> {
    public void method(Xyz x) {
        x.inInt();
    }


    public void method1(final Xyz x) {
        x.m1();
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