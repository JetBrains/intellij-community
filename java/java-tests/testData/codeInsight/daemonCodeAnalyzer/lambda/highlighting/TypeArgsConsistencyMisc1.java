import java.util.List;
class Test1 {
    
    interface I<X> {
        X foo(List<String> list);
    }
    
    static <T> I<T> bar(I<T> i){return i;}
    static <T> void bar1(I<T> i){}
    static <T> void bar2(T t, I<T> i){}
    static <T> void bar3(I<T> i, T t){}
    
    {
        bar(x -> x);
        bar1(x -> x);

        I<Object> lO =  x->x;
        bar2("", lO);

        <error descr="Incompatible types. Found: '<lambda expression>', required: 'Test1.I<java.lang.String>'">I<String> lS =  x->x;</error>
        bar2("", lS);

        bar2("", x -> x);

        bar3(x -> x, "");
    }
}


class Test2 {

    interface I<X> {
        X foo(List<X> list);
    }

    static <T> I<T> bar(I<T> i){return i;}
    static <T> void bar1(I<T> i){}
    static <T> void bar2(T t, I<T> i){}
    static <T> void bar3(I<T> i, T t){}

    {
        bar(<error descr="Cyclic inference">x -> x</error>);
        bar1(<error descr="Cyclic inference">x -> x</error>);
        bar2<error descr="'bar2(java.lang.Integer, Test2.I<java.lang.Integer>)' in 'Test2' cannot be applied to '(int, <lambda expression>)'">(1, x -> x)</error>;
        bar2<error descr="'bar2(java.lang.String, Test2.I<java.lang.String>)' in 'Test2' cannot be applied to '(java.lang.String, <lambda expression>)'">("", x -> x)</error>;
        bar3<error descr="'bar3(Test2.I<java.lang.String>, java.lang.String)' in 'Test2' cannot be applied to '(<lambda expression>, java.lang.String)'">(x -> x, "")</error>;
    }
}

class Test3 {

    interface I<X> {
        List<X> foo(List<X> list);
    }

    static <T> I<T> bar(I<T> i){return i;}
    static <T> void bar1(I<T> i){}
    static <T> void bar2(T t, I<T> i){}
    static <T> void bar3(I<T> i, T t){}

    {
        bar(<error descr="Cyclic inference">x -> x</error>);
        bar1(<error descr="Cyclic inference">x -> x</error>);
        bar2(1, x -> x);
        bar2("", x -> x);

        bar3(x -> x, "");
    }
}
