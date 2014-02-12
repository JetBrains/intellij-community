import java.util.*;
interface C<T> extends List<List<T>>{}
abstract class A {
    abstract <T> T baz(List<? super List<? super T>> a);

    void bar(C<?> x){
        baz<error descr="'baz(java.util.List<? super java.util.List<? super T>>)' in 'A' cannot be applied to '(C<capture<?>>)'">(x)</error>;
    }
}
