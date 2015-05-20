public class Test {
  interface Predicate<T> {
    boolean test(T t);
  }

  {
    Predicate<? super Integer> p = (Number n) -> n.equals(23);
    Predicate<Integer> p1 = <error descr="Incompatible parameter types in lambda expression: expected Integer but found Number">(Number n)</error> -> n.equals(23);
    Predicate<Number> p2 = (Number n) -> n.equals(23);
  }
}