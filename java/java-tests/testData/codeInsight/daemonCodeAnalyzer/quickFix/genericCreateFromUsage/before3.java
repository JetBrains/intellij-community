// "Create Method 'foo'" "true"
interface Int<T> {
}

class A1<T> implements Int<T> {
}

class B1 {
    A1<String> a;
    void foo (Int<String> c) {
        a.<caret>foo(c);
    }
}