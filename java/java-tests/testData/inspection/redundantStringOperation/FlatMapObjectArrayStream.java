import java.util.stream.Stream;

// IDEA-294679
public class Demo {
  public static void main(String[] args) {
    Stream.of(1)
      .map(x -> Stream.<Object[]>of(new Object[] {x}))
      .flatMap(t -> t)
      .forEach(System.out::println);
  }
}
