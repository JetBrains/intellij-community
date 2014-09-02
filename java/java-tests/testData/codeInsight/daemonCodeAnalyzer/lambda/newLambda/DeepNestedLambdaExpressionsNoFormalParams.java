
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;


abstract class Simplified {
  public List<Integer> gerFirstTriangles() {
    return flatMap(() -> flatMap(() -> map(z -> 1))).collect(Collectors.toList());
  }

  abstract <R> R flatMap(Supplier<R> mapper);
  abstract <R> Stream<R> map(Function<Integer, R> mapper);
}

class PythagoreanTriangles {

  static class Triplet<T,U,R>{
    private T t;
    private U u;
    private R r;

    public Triplet(T t, U u, R r){
      this.t = t;
      this.u = u;
      this.r = r;
    }

    @Override
    public String toString() {
      return t.toString() + "," + u.toString() + "," + r.toString();
    }
  }

  public void pythagoreanTriangles(Integer num){
    Stream<Integer> numbers = IntStream.rangeClosed(1,num).boxed();

    Stream<Triplet<Integer, Integer, Integer>> triangles = numbers.flatMap(x -> {
      return IntStream.rangeClosed(1, x).boxed().flatMap(y -> {
        return IntStream.rangeClosed(1, y).boxed().filter(z -> {
          return x * x == y * y + z * z;
        }).map(z1 -> new Triplet<Integer, Integer, Integer>(x, y, z1));
      });
    });

    triangles.forEach(System.out::println);

  }

  public List<Triplet<Integer, Integer, Integer>> gerFirstTriangles(long num){
    Stream<Integer> infinity = Stream.iterate(1, (n) -> n + 1);
    return infinity.flatMap(x -> {
      return IntStream.rangeClosed(1, x).boxed().flatMap(y -> {
        return IntStream.rangeClosed(1, y).boxed().filter(z -> {
          return x * x == y * y + z * z;
        }).map(z1 -> new Triplet<Integer, Integer, Integer>(x, y, z1));
      });
    }).limit(num).collect(Collectors.toList());
  }


  public static void main(String[] args) {
    PythagoreanTriangles triangles = new PythagoreanTriangles();
    triangles.pythagoreanTriangles(10);
    triangles.gerFirstTriangles(5l).forEach(System.out::println);
  }
}
