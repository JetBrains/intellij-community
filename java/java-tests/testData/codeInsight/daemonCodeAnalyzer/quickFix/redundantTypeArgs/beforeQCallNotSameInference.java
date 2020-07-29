// "Remove type arguments" "false"
import java.util.function.Function;

class MyTest {

  public JBIterable<String> getChildren(JBIterable<? extends Integer> children) {
    return children
      .map(this.<St<caret>ring>wrapper())
      .filter();
  }

  protected <De extends String> Function<Integer, De> wrapper() {
    return null;
  }

  abstract class JBIterable<E>{
    public final <T> JBIterable<T> map(Function<? super E, ? extends T> function) {
      return null;
    }

    public final JBIterable<E> filter() {
      return null;
    }
  }
}