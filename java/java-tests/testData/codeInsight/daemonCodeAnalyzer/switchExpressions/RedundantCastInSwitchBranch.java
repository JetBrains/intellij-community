import java.util.function.Predicate;
import java.util.*;

class RedundantCast {
  private static void foo(final int matchType) {
    Object o = switch (matchType) {
      default -> (Predicate<Object>) target -> target == null;
    };
    Predicate<Object> o1 = switch (matchType) {
      default -> (<warning descr="Casting 'target -> {...}' to 'Predicate<Object>' is redundant">Predicate<Object></warning>) target -> target == null;
    };
    
    Predicate<Object> o2 = switch (matchType) {
      default:
        yield (<warning descr="Casting 'target -> {...}' to 'Predicate<Object>' is redundant">Predicate<Object></warning>) target -> target == null;
    };
  }

  @SuppressWarnings("unchecked")
  <T> List<T> getList1(int x) {
    return (List<T>) switch(x) {
      case 0 ->  new ArrayList<>();
      default -> new ArrayList<Integer>();
    };
  }
  
  @SuppressWarnings("unchecked")
  <T> List<T> getList2(int x) {
    return (<warning descr="Casting 'switch (x) {...}' to 'List<T>' is redundant">List<T></warning>) switch(x) {
      case 0 ->  new ArrayList<>();
      default -> new ArrayList<>();
    };
  }

  void castForFunctionalExpression(String s) {
    (switch (s) {
      case "a" -> (Runnable)() -> System.out.println("a");
      case "b" -> (Runnable)() -> System.out.println("b");
      default -> throw new IllegalArgumentException();
    }).run();
  }
}