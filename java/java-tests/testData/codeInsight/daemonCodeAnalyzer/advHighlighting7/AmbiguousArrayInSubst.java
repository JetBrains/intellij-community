package pck;

import java.util.List;

import static pck.C.foo;
import static pck.D.foo;

class C {
    static <T> void foo(List<T> x) { }
}

class D {
    static <T> String foo(List<T[]> x) { return null; }
}

class Main {
    public static void main(String[] args){
        List<String[]> x = null;
        foo(x).toLowerCase();
    }
}
