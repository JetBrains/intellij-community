// "Create Method 'foo'" "true"
interface Int<T> {
}

class A1<T> implements Int<T> {
    public void foo(Int<T> c) {
        <selection>//To change body of created methods use File | Settings | File Templates.</selection><caret>
    }
}

class B1 {
    A1<String> a;
    void foo (Int<String> c) {
        a.foo(c);
    }
}