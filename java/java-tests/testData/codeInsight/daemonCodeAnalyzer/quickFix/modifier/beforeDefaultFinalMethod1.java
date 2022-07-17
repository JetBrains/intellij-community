// "Make 'x' not final" "true"
interface X {
  default final<caret> void x() {}
}
