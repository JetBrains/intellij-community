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
        String o = <error descr="Incompatible types. Found: 'capture<?>', required: 'java.lang.String'">baz(x);</error>
    }
}
