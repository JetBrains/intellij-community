// "Make 'x' not final" "true"
interface X {
  default<caret> final void x() {}
}
