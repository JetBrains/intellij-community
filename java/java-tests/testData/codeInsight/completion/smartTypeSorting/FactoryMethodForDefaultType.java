import java.util.*;

public class Foo {

    {
        Map<Pair<Object, Object>, Object> map;
        map.get(<caret>)
    }

}

class Pair<A, B> {
    Pair(A a, B b) {}

    static <A,B> Pair<A,B> create(A a, B b) {}
}
