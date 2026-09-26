// "Replace cast expressions with pattern variable" "true"
public final class A {
  private static void foo(Object o) {
    int x = o instanceof String ? ((Strin<caret>g)o).length() : 0;
  }
}