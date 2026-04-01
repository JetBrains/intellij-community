// "Make 'Interface.f()' static" "true-preview"
abstract interface Interface {
  int f();

  static void y() {
    <caret>f(); // <- Error
  }
}