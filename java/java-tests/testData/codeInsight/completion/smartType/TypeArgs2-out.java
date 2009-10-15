import java.util.Collection;

class List<T> {}

public class A {
    void f() {
        class Collection {}
        List<java.util.Collection> l = new List<java.util.Collection>();
    }
}
