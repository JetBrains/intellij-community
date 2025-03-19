// "Replace cast expressions with pattern variable" "true"
public final class A {
  private static void foo(Object o) {
    int x = o instanceof String string ? string.length() : 0;
  }
}