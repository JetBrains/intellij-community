import java.util.ArrayList;
import java.util.List;

interface A{};
interface B extends A{};

class GenericTest {
  public static <M extends V, V> List<V> convert(List<M> list){
    return new ArrayList<V>();
  }

  public static void test(){
    List<A> as = convert(new ArrayList<B>());
  }
}