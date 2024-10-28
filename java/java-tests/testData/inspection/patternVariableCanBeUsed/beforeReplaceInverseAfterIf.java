// "Replace cast expressions with pattern variable" "true"
public final class A {
  private static boolean foo4(Number o, Number n) {
    if (!(o instanceof Integer)) {
      return false;
    } else if (((In<caret>teger) o).describeConstable().get() == 1) {
      return true;
    }

    if (((Integer) o).describeConstable().get() == 2) {
      return true;
    }

    return ((Integer) o).describeConstable().get() == 3;
  }

}