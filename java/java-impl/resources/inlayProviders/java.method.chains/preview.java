abstract class Foo<T> {
  void main() {
    Stream.of(1, 2, 3).filter(x -> x % 2 == 0) <# Foo<Integer> #>
      .map(x -> x * 2)<# Foo<Integer> #>
      .map(x -> "item: " + x)<# Foo<Object> #>
      .forEach(System.out::println);
  }
}