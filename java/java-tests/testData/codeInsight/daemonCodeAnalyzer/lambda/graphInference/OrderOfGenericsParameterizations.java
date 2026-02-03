
import java.util.*;

class MyTest {
    {
        consume(new HashSet<ListProperty<?>>());
    }

    static <_L extends ListProperty<String>> void consume(HashSet<? super _L> a) {}
}
interface ListProperty<A> extends Set<List<A>>{}
