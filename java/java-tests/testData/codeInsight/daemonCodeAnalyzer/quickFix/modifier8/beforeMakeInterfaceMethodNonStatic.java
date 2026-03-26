// "Make 'y()' not static" "true"
abstract interface Interface {
  int f();

  static void y() {
    <caret>f(); // <- Error
  }
}