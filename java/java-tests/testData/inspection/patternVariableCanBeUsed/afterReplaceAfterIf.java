// "Replace cast expressions with pattern variable" "true"
public final class A {
  private static boolean foo3(Number o, Number n) {
    if (!(o instanceof Integer integer)) {
      return false;
    }

    if (integer.describeConstable().get() == 1) {
      return true;
    }
    if (integer.describeConstable().get() == 2) {
      return true;
    }
    return false;
  }
}