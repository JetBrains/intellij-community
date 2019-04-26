package pck;

import java.util.List;

import static pck.C.foo;
import static pck.C.foo1;
import static pck.D.foo;
import static pck.D.foo1;

class C {
    static <T> void foo(List<T> x) { }
    static <T extends List> void foo1(List<T> x) { }
}

class D {
    static <T extends List<S>, S> String foo(List<T> x) { return null; }
    static <T extends List<?>, S> String foo1(List<T> x) { return null; }
}

class Main {
    public static void main(String[] args){
        List<List<String>> x = null;
        foo(x).toCharArray();
        foo1(x).toCharArray();
    }
}
