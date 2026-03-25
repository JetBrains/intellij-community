// "Make 'Interface.f()' static" "true-preview"
abstract interface Interface {
    static int f() {
        return 0;
    }

    static void y() {
    f(); // <- Error
  }
}