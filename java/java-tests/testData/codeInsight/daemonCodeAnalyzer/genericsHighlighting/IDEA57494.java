import java.util.*;
abstract class A {
    abstract <T> T baz(List<? super List<? super T>> a);

    void bar(List<List<?>> x){
        String s = baz(x);
    }
}

abstract class A1{
    abstract <T> T baz(List<? super T> a);

    void bar(List<?> x){
        String o = baz<error descr="'baz(java.util.List<? super java.lang.String>)' in 'A1' cannot be applied to '(java.util.List<capture<?>>)'">(x)</error>;
    }
}