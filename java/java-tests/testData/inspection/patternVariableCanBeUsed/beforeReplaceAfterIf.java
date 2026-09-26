// "Replace cast expressions with pattern variable" "true"
public final class A {
  private static boolean foo3(Number o, Number n) {
    if (!(o instanceof Integer)) {
      return false;
    }

    if (((Int<caret>eger) o).describeConstable().get() == 1) {
      return true;
    }
    if (((Integer) o).describeConstable().get() == 2) {
      return true;
    }
    return false;
  }
}