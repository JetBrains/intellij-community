import java.util.*;

interface A2{};
interface B extends A2{};

class GenericTest {
    public static <M extends V, V> List<V> convert(List<M> list){
        return new ArrayList<V>();
    }
    public static void test(){
        // it prompts convert returns List<B>
        List<A2> as = convert(new ArrayList<B>());
    }
}
