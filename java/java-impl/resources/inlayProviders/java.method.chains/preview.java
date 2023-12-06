import java.util.stream.*;
class Foo {
  {
    Stream.of(1, 2, 3).filter(x -> x % 2 == 0)/*<# Stream<Integer> #>*/
      .map(x -> x * 2)/*<# Stream<Integer> #>*/
      .map(x -> "item: " + x)/*<# Stream<String> #>*/
      .forEach(System.out::println);
  }
}