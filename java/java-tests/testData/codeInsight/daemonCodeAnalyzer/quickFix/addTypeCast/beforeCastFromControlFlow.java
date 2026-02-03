// "Cast qualifier to 'java.lang.String'" "true-preview"
class Test {
  void m(Object o) {
    if (o instanceof String) {
      System.out.println(<caret>o.length());
    }
  }
}