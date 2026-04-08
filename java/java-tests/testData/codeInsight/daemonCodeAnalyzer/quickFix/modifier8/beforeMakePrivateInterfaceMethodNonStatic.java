// "Make 'y()' not static" "true"
abstract interface Interface {
  int f();

  private static void y() {
    <caret>f(); // <- Error
  }
}