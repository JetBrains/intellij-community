// "Simplify 'my.value() == null' to false" "true"
class Test {
  void some(SuppressWarnings my) {
    if (my == null || my.value() =<caret>= null) {
      System.out.println("null");
    }
  }
}