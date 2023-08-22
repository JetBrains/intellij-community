// "Make 'a.f()' not abstract" "true-preview"
interface a {
    default String f() {
        return null;
    }
}
class b implements a {
  void z() {
    a.super.f();
  }
}

