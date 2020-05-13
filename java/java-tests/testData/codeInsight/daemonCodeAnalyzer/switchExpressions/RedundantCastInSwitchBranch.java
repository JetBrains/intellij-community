import java.util.function.Predicate;

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
}