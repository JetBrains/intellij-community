import java.util.*;

class Test {
    interface I<T extends String, V extends List<T>> {
        T m(V p);
    }

    void foo(I<? extends String, ? extends List<? extends String>> fip) { }

    void test() {
        foo(<error descr="Cannot infer functional interface type">(ArrayList<? extends String> p) -> p.get(0)</error>);
    }
}

