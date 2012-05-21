// "Create Method 'foo'" "true"
interface Comparable<T> {
}

class R implements Comparable<R> {

}

class A1<T> {
    public T foo() {
        <caret><selection>return null;  //To change body of created methods use File | Settings | File Templates.</selection>
    }
}

class B1 {
    A1<R> a;
    void foo (Comparable<R> c) {
        c = a.foo();
    }
}