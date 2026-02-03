// "Create method 'getPreloadKeys'" "true-preview"
import java.util.*;

class Foo<T>{

    void test(Foo<String> f){
        f.foo(getPreloadKeys());
    }

    private Collection<Collection<? extends String>> getPreloadKeys() {
        return null;
    }

    void foo(Collection<Collection<? extends T>> c) {}
}