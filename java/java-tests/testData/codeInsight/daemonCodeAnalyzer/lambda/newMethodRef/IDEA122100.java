import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

class InferenceExample<E> {
  public <Output> List<Output> convertAll(Function<E, Output> converter) {
    return invoke (MyArrayList::convertAll, converter);
  }

  protected <A, R> R invoke(BiFunction<MyArrayList<E>, A, R> action, A arg) {
    return null;
  }

  public class MyArrayList<T> extends ArrayList<T> {
    public <Output1> List<Output1> convertAll(Function<T,  Output1> converter) {
      return null;
    }
  }
}