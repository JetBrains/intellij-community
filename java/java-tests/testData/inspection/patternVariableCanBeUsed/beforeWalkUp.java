// "Replace cast expressions with pattern variable" "true"
public final class A {
  public void test(Object o) {
    if (o instanceof String) {
      System.out.println(((String) o).isEmpty());
      for (int i = 0; i < ((String) o).length(); i++) {
        System.out.println(((Strin<caret>g) o).charAt(i));
      }
    }
  }
}