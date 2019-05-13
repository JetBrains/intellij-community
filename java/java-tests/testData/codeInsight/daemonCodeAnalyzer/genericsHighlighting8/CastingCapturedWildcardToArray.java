
import java.util.List;

class Test2 {
  private static void test(List<?> list, F<?> f, F<? super Runnable> fs, F<? extends Test2> fe) {
    boolean isObjectArray = list.get(0) instanceof Object[];
    boolean isObjectArray1 = <error descr="Inconvertible types; cannot cast 'capture<?>' to 'java.lang.Object[]'">f.get() instanceof Object[]</error>;
    boolean isObjectArrays = <error descr="Inconvertible types; cannot cast 'capture<? super java.lang.Runnable>' to 'java.lang.Object[]'">fs.get() instanceof Object[]</error>;
    boolean isObjectArraye = <error descr="Inconvertible types; cannot cast 'capture<? extends Test2>' to 'java.lang.Object[]'">fe.get() instanceof Object[]</error>;
  }

  private static void test(G<?> g,
                           G<? super Cloneable> gs,
                           G<? extends Test2> ge,
                           G<? extends Test2[]> gea) {
    boolean isObjectArray1 = g.get() instanceof Object[];
    boolean isObjectArrays = gs.get() instanceof Object[];
    boolean isObjectArraye = <error descr="Inconvertible types; cannot cast 'capture<? extends Test2>' to 'java.lang.Object[]'">ge.get() instanceof Object[]</error>;
    boolean isObjectArrayea = gea.get() instanceof Object[];
  }

  class F<T extends Runnable> {
    public T get() {
      return null;
    }
  }

  class G<T extends Cloneable> {
    public T get() {
      return null;
    }
  }
}