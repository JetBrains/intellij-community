// "Replace cast expressions with pattern variable" "true"
public final class A {
  private static boolean foo2(Number o, Number n) {
    return o instanceof Integer integer && o != n && integer.describeConstable().get() == 1;
  }
}