import java.util.List;
import java.util.function.Function;

class Test {
  void foo(Function<String, List<String>> f) {
    DS<List<String>> ds = flatMap(f);
  }


  public <R> Operator<R, ?> flatMap(Function<String, R> flatMapper) {
    return null;
  }
}

class Operator<OUT, O extends Operator<OUT, O>> extends DS<OUT> {}
class DS<OUT> {}