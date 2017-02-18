// "Remove redundant types" "true"
class Test2 {
    class Y<T>{
        T t;
    }

    interface I<X> {
        X foo(Y<X> list);
    }

    static <T> I<T> bar(I<T> i){return i;}

    {
        Test2.<String>bar(y -> y.t);
    }
}