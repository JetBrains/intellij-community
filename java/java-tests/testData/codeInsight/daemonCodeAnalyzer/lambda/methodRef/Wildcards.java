import java.util.*;

class MyTest2 {
    {
        Comparator<? super String> comparator = String::compareToIgnoreCase;
    }
}

class Test {
    void test() {
       Foo2<? extends Bar2> foo2 = Bar2::xxx;
    }
}

interface Foo2<T> {
    void bar(T i, T j);
}
interface Bar2 {
    default void xxx(Bar2 p) { }
}
