import java.util.function.Predicate;

class SwitchExpr {
  void test() {
    Predicate<String> predicate = value -> switch(value) {
      case "A" -> true;
      default -> false;
    };
    Predicate<String> otherPredicate = value -> value.length() == 1 && predicate.test(value);
  }
}
