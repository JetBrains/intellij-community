// "Create Method 'foo'" "true"
interface Comparable<T> {
}

class R implements Comparable<R> {

}

class A1<T> {
}

class B1 {
    A1<R> a;
    void foo (Comparable<R> c) {
        c = a.<caret>foo();
    }
}