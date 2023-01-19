// "Create method 'getPreloadKeys'" "true-preview"
import java.util.*;

class Foo<T>{

    void test(Foo<String> f){
        f.foo(getPrel<caret>oadKeys());
    }

    void foo(Collection<Collection<? extends T>> c) {}
}