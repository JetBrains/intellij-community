package pck;

import java.util.Collection;
import java.util.List;

import static pck.D.foo;
import static pck.C.foo;

class C {
    static <T extends Collection<S>, S> void foo(T x) { }
}

class D {
    static <T extends List<S>, S> String foo(T x) { return null; }
}

class B{
    public static void bar(){
        foo(null).toLowerCase();
    }
}