// "Make 'y())' not static" "false"
abstract interface Interface {
  int f();

  static void y() {
    <caret>f(); // <- Error
  }
}