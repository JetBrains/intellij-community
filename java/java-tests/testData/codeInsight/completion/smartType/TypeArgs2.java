class List<T> {}

public class A {
    void f() {
        class Collection {}
        List<java.util.Collection> l = new <caret>
    }
}
