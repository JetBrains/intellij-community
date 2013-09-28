class A1<T> {}
class B1<T extends A1<? super A1<? super T>>>{
    {
        T a = null;
        <error descr="Incompatible types. Found: 'T', required: 'A1<? super T>'">A1<? super T> b = a;</error>
    }
}

class A<T> {}
class B<T extends A<? super A<? super T>>> {

    void bar(T x){
        foo(x);
    }
    void foo(A<? super T> x){}
    void foo(Object x){}
}
