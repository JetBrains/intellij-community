// IDEA-394095: a type parameter must not be treated as a permitted subclass of its own bound
public sealed abstract class SwitchExhaustivenessWithRecursiveGenerics<T extends SwitchExhaustivenessWithRecursiveGenerics<T>> {
  public static sealed abstract class Bar<T extends Bar<T>> extends SwitchExhaustivenessWithRecursiveGenerics<T> {
  }

  public static sealed abstract class Baz<T extends Baz<T>> extends SwitchExhaustivenessWithRecursiveGenerics<T> {
  }

  public static final class Alpha extends Baz<Alpha> {
  }

  public static final class Beta extends Bar<Beta> {
  }

  public static boolean exhaustive(SwitchExhaustivenessWithRecursiveGenerics<?> step) {
    return switch (step) {
      case Alpha ignored -> true;
      case Beta ignored -> false;
    };
  }

  public static boolean exhaustiveViaIntermediateClass(SwitchExhaustivenessWithRecursiveGenerics<?> step) {
    return switch (step) {
      case Bar<?> ignored -> true;
      case Baz<?> ignored -> false;
    };
  }

  public static boolean notExhaustive(SwitchExhaustivenessWithRecursiveGenerics<?> step) {
    return switch (<error descr="'switch' expression does not cover all possible input values">step</error>) {
      case Beta ignored -> false;
    };
  }
}
