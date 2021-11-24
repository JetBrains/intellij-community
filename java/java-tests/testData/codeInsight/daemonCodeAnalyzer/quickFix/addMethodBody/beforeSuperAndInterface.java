// "Make 'a.f' not abstract" "true"
interface a {
   String f();
}
class b implements a {
  void z() {
    a.super.<caret>f();
  }
}

