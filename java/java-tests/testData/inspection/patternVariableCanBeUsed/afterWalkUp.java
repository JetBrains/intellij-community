// "Replace cast expressions with pattern variable" "true"
public final class A {
  public void test(Object o) {
    if (o instanceof String string) {
      System.out.println(string.isEmpty());
      for (int i = 0; i < string.length(); i++) {
        System.out.println(string.charAt(i));
      }
    }
  }
}