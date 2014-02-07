import java.util.*;

class Test {
    interface I<T extends String, V extends List<T>> {
        T m(V p);
    }

    void foo(I<? extends String, ? extends List<? extends String>> fip) { }

    void test() {
        foo<error descr="'foo(Test.I<? extends java.lang.String,? extends java.util.List<? extends java.lang.String>>)' in 'Test' cannot be applied to '(<lambda expression>)'">((ArrayList<? extends String> p) -> p.get(0))</error>;
    }
}

