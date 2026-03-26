// "Make 'y()' not static" "true"
abstract interface Interface {
  int f();

  default void y() {
    f(); // <- Error
  }
}