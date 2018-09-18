// "Cast qualifier to 'java.lang.String'" "true"
class Test {
  void m(Object o) {
    if (o instanceof String) {
      System.out.println(((String) o).length());
    }
  }
}