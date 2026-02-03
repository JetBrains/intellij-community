
interface A<T> { }

class B<T> implements A<T> {}

class C {
    void bar(A<A<?>> x){
        B<A<String>> y = <error descr="Inconvertible types; cannot cast 'A<A<?>>' to 'B<A<java.lang.String>>'">(B<A<String>>) x</error>;
    }
}

//-----------------------
interface A2<T> { }

class B2<T> implements A2<T> {}

class C2 {
    void bar(A2<A2> x){
        B2<A2<?>> y = <error descr="Inconvertible types; cannot cast 'A2<A2>' to 'B2<A2<?>>'">(B2<A2<?>>) x</error>;
    }
}

//-----------------------
interface A3<T> { }

class B3<T> implements A3<T> {}

class C3 {
    <T> void bar(A3<A3<T>> x){
        A3<A3<?>> y = <error descr="Inconvertible types; cannot cast 'A3<A3<T>>' to 'A3<A3<?>>'">(A3<A3<?>>) x</error>;

    }
}