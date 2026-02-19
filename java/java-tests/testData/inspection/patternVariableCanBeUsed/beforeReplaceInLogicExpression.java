// "Replace cast expressions with pattern variable" "true"
public final class A {
  private static boolean foo2(Number o, Number n) {
    return o instanceof Integer && o != n && ((Integ<caret>er) o).describeConstable().get() == 1;
  }
}