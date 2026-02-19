// "Make 'a.f()' not abstract" "true-preview"
interface a {
   String f();
}
class b implements a {
  void z() {
    a.super.<caret>f();
  }
}

