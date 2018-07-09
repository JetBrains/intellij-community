
import java.util.List;

class Main {

    void fooExtends(List<? extends String> list) {
        var x = list.get(0);
        x = "";
        var y = list;
        y.add<error descr="'add(capture<? extends java.lang.String>)' in 'java.util.List' cannot be applied to '(java.lang.String)'">("")</error>;
    }
    
    void fooSuper(List<? super String> list) {
        var x = list.get(0);
        x = "";
        var y = list;
        y.add("");
    }
    
    void fooX(X<? extends String, String> x) {
        var v = x;
        v.add<error descr="'add(capture<? extends java.lang.String>)' in 'X' cannot be applied to '(java.lang.String)'">("")</error>;

        var e = x.get();
        e = "";
    }
    
    void fooXArray(X<? extends String, String> x) {
        var v = m(x.get());
        String s = v[1];
        v[1] = s;

        var e = m(x);
        e[0].add<error descr="'add(capture<? extends java.lang.String>)' in 'X' cannot be applied to '(java.lang.String)'">("")</error>;
    }

    void fooHierarchy(X<? extends B, A> x) {
        var y = x;
        y = new X<B, A>();
        y = new X<C, A>();

        A a = y.get();
        B b = y.get();
        <error descr="Incompatible types. Found: 'capture<? extends B>', required: 'C'">C c = y.get();</error>
    }

    <M> M[] m(M m) {
        return null;
    }
}

class A {}
class B extends A {}
class C extends B {}

class X<T extends S, S> {
    T get() {return null;}
    void add(T t) {}
}

class RecursiveBound {
    class C<T extends Comparable<T>> { }

    void test(C<? super Integer> c) {
        var x = c;
    }
}