// "Create local variable 'h'" "true-preview"
class X {
  
  void x() {
    int month = (h<caret> + L - 7 * m + 114) / 31;
    int day = ((h + L - 7 * m + 114) % 31) + 1;
  }
}