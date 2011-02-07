import java.util.*;

class Foo<T> {
    void f(ArrayList<T> t) {}
    <V> void f(List<V> t) {}
}

class User {
    void foo (Foo<String> foo) {
       foo.<ref>f(new ArrayList<String>());
    }
}