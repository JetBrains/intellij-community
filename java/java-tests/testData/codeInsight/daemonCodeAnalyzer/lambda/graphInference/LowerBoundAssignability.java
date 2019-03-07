
import java.util.*;

class MyTest {

    {
        List<A<? super Set<String>>> stream =
                Arrays.asList(new A<Set<?>>(), new A<Set<String>>());
    }

    static class A<T> { }
}
