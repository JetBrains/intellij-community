
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

class OuterClass<E> {
    private class InnerClass {}

    private static <T> void someMethod(OuterClass<T>.InnerClass inner) {}

    static <T1> void callSomeMethod(OuterClass<T1>.InnerClass inner) {
        someMethod(inner);
    }
}

class Outer<T> {
    public static <U> Outer<U> loopback(Outer<U>.Inner u){
        return foo(u);
    }
    private static <T> Outer<T> foo(Outer<T>.Inner u){
        return null;
    }
    private class Inner{}
}


class Outer1<O> {
    public static <T0> void loopback(List<Outer1<T0>.Inner> u){
        Outer1<T0>.Inner a = foo(u);
    }
    private static <T> Outer1<T>.Inner foo(List<Outer1<T>.Inner> u){
        return null;
    }
    private class Inner {}
}

class Outer2<O> {
    public static <T0> void loopback(List<? extends Outer2<T0>.Inner> u){
        Outer2<? extends T0>.Inner a = foo(u);
    }

    private static <T> Outer2<? extends T>.Inner foo(List<? extends Outer2<T>.Inner> u){
        return null;
    }
    private class Inner {}
}


class Outer3<O> {
    {
        bar(Outer3.Inner::new, new ArrayList<>());
        bar(Inner::new, new ArrayList<>());
    }

    private <K> void bar(final Supplier<Outer3<K>.Inner> s, List<K> l) {

    }

    private class Inner {}
}