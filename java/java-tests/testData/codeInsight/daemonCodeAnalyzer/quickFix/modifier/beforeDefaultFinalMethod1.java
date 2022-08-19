// "Make 'x()' not final" "true-preview"
interface X {
  default final<caret> void x() {}
}
