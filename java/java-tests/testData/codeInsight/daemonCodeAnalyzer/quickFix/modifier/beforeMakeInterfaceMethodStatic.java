// "Make 'Interface.f()' static" "false"
abstract interface Interface {
  int f();

  static void y() {
    <caret>f(); // <- Error
  }
}