// "Replace cast expressions with pattern variable" "true"
public final class A {
  private static boolean foo4(Number o, Number n) {
    if (!(o instanceof Integer integer)) {
      return false;
    } else if (integer.describeConstable().get() == 1) {
      return true;
    }

    if (integer.describeConstable().get() == 2) {
      return true;
    }

    return integer.describeConstable().get() == 3;
  }

}