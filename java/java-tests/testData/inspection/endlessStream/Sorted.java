import java.util.stream.LongStream;

public class Sorted {
  public static void main(String[] args) {
    long sum1 = LongStream.iterate(0, i -> i + 1).<warning descr="All elements consuming operation applied on infinite stream">sorted</warning>().limit(10).sum();
    long sum2 = LongStream.generate(() -> 10).<warning descr="All elements consuming operation applied on infinite stream">sorted</warning>().limit(10).sum();
  }
}