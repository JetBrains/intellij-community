class C<T extends Runnable&<error descr="Interface expected here">Exception</error>,U> {

}

class Stuff<X extends Stuff & Runnable> {
    <T, V extends T & <error descr="Type parameter cannot be followed by other bounds">Runnable</error>> T method(V v) {
        return null;
    }

    <T extends X & <error descr="Type parameter cannot be followed by other bounds">Runnable</error> & <error descr="Type parameter cannot be followed by other bounds">Comparable</error>> void f(T t) {

    }

    <T extends Stuff & Runnable & Comparable> void f2(T t) {

    }
    <T extends Runnable & Comparable> void f3(T t) {

    }
}

////////////////
public class TypeParameters {
    class X {}
    static <T extends X> void f(Class<T> t){}

    static {
        f(X.class);
    }
}
class Typr {
  <T extends TypeParameters.X> void f() {}
}
