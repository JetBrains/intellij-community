import java.util.*;
class Test {
    B f;

    A bar(Set<A> s) {
        s.add(f);
        return f.foo(s);
    }
}
class A {
    <T> T foo(Set<T> t) {
        return null;
    }
}

class B extends A {
}