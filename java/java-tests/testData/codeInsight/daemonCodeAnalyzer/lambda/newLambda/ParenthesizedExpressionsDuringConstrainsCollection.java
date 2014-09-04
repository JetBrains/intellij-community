import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

abstract class Test {
  public List<Integer> gerFirstTriangles() {
    return flatMap((y -> (map((z1 -> (1)))))).collect(Collectors.toList());
  }

  abstract <R> R         flatMap(Function<Integer, R> mapper);
  abstract <R> Stream<R> map    (Function<Integer, R> mapper);
}