import org.jetbrains.annotations.Nullable;

class MatchExceptionWithPrimitivePatterns {

  interface Case {
    String run();
  }

  static void check(String name, Case c) {
    try {
      System.out.println(name + " => OK[" + c.run() + "]");
    }
    catch (Throwable t) {
      System.out.println(name + " => " + t.getClass().getSimpleName());
    }
  }

  record Point(int x, int y) {
  }

  record IntBox(@Nullable Integer v) {
  }

  record LongBox(@Nullable Long v) {
  }

  record DoubleBox(@Nullable Double v) {
  }

  record BoolBox(@Nullable Boolean v) {
  }

  record WidenBox(@Nullable Integer v) {
  }      // Integer component matched by a wider primitive

  record PointBox(@Nullable Point p) {
  }

  static void main() {
    check("primInt", () -> primInt(new IntBox(null)));
    check("primIntWithUncond", () -> primIntWithUncond(new IntBox(null)));
    check("primLong", () -> primLong(new LongBox(null)));
    check("primLongWithUncond", () -> primLongWithUncond(new LongBox(null)));
    check("primDouble", () -> primDouble(new DoubleBox(null)));
    check("primDoubleWithUncond", () -> primDoubleWithUncond(new DoubleBox(null)));
    check("primBoolean", () -> primBoolean(new BoolBox(null)));
    check("primWiden", () -> primWiden(new WidenBox(null)));

    check("recordComponent_control", () -> recordComponent_control(new PointBox(null)));
    check("typePattern_control", () -> typePattern_control(new PointBox(null)));
  }

  static String primInt(IntBox b) {
    return switch (b) {
      case <warning descr="Switch label 'IntBox(int p)' is the only reachable in the whole switch">IntBox(<warning descr="Pattern matching may throw 'MatchException'">int p</warning>)</warning> -> "int:" + p;
    };
  }

  static String primIntWithUncond(IntBox b) {
    return switch (b) {
      case IntBox(int p) -> "int:" + p;
      default -> "primIntWithUncond(" + b + ")";
    };
  }

  static String primLong(LongBox b) {
    return switch (b) {
      case <warning descr="Switch label 'LongBox(long p)' is the only reachable in the whole switch">LongBox(<warning descr="Pattern matching may throw 'MatchException'">long p</warning>)</warning> -> "long:" + p;
    };
  }

  static String primLongWithUncond(LongBox b) {
    return switch (b) {
      case LongBox(long p) -> "long:" + p;
      case LongBox(Long v) -> "primLongWithUncond(" + v + ")";
    };
  }

  static String primDouble(DoubleBox b) {
    return switch (b) {
      case <warning descr="Switch label 'DoubleBox(double p)' is the only reachable in the whole switch">DoubleBox(<warning descr="Pattern matching may throw 'MatchException'">double p</warning>)</warning> -> "double:" + p;
    };
  }

  static String primDoubleWithUncond(DoubleBox b) {
    return switch (b) {
      case DoubleBox(double p) -> "double:" + p;
      case DoubleBox(Object p) -> "primDoubleWithUncond(" + p + ")";
    };
  }

  static String primBoolean(BoolBox b) {
    return switch (b) {
      case <warning descr="Switch label 'BoolBox(boolean p)' is the only reachable in the whole switch">BoolBox(<warning descr="Pattern matching may throw 'MatchException'">boolean p</warning>)</warning> -> "bool:" + p;
    };
  }

  static String primWiden(WidenBox b) {
    return switch (b) {
      case WidenBox(<warning descr="Pattern matching may throw 'MatchException'">float p</warning>) -> "widen float:" + p;
      case WidenBox(double p) -> "widen:" + p;
    };
  }

  static String recordComponent_control(PointBox b) {
    return switch (b) {
      case <warning descr="Switch label 'PointBox(Point(var x, var y))' is the only reachable in the whole switch">PointBox(<warning descr="Pattern matching may throw 'MatchException'">Point(var x, var y)</warning>)</warning> -> "pt:" + x + "," + y;
    };
  }

  static String typePattern_control(PointBox b) {
    return switch (b) {
      case <warning descr="Switch label 'PointBox(Point p)' is the only reachable in the whole switch">PointBox(Point p)</warning> -> "p=" + p;
    };
  }
}