import java.util.stream.IntStream;

public class Limited {
  public static void main(String[] args) {
    int sum1 = IntStream.iterate(0, i -> i + 1).limit(10).sum();
    int sum2 = IntStream.iterate(0, i -> i < 10, i -> i + 1).sum();
    System.out.println(IntStream.iterate(0, i -> i + 1).takeWhile(value -> value < 54).sorted().sum());
  }
}