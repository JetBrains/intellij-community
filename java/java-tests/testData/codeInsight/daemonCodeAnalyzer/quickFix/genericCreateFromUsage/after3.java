// "Create method 'foo'" "true"
interface Int<T> {
}

class A1<T> implements Int<T> {
    public void foo(Int<T> c) {
        
    }
}

class B1 {
    A1<String> a;
    void foo (Int<String> c) {
        a.foo(c);
    }
}