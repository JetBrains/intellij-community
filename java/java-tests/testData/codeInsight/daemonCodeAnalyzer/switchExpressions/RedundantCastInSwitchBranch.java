import java.util.function.Predicate;

class RedundantCast {
  private static void foo(final int matchType) {
    Object o = switch (matchType) {
      default -> (Predicate<Object>) target -> target == null;
    };
  }
}