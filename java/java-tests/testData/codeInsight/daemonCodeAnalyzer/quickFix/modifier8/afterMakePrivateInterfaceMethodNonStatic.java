// "Make 'y()' not static" "true"
abstract interface Interface {
  int f();

  private void y() {
    f(); // <- Error
  }
}