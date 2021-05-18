// "Simplify 'my.value() == null' to false" "true"
class Test {
  void some(SuppressWarnings my) {
    if (my == null) {
      System.out.println("null");
    }
  }
}