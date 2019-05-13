import java.util.Objects;
import java.util.function.Predicate;

class Main {
  Predicate<String> m() {
    Predicate<Object> qwe = o -> true;

    Predicate<String> p1 = ((Predicate<String>) s -> false).or(s -> Objects.equals(s, "asd"));

    Predicate<String> not = ((Predicate<String>) s -> s != null).negate();
    return not;

  }
}