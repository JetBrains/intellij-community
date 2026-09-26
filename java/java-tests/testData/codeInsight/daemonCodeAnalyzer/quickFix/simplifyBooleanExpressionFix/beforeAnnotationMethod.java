// "Simplify 'my.value() == null' to false" "false"
class Test {
  void some(SuppressWarnings my) {
    if (my == null || my.value() =<caret>= null) {
      System.out.println("null");
    }
  }
}