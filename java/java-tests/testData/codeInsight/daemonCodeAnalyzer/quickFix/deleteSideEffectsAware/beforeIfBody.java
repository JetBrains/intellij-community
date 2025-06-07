// "Extract side effects as an 'if' statement" "true-preview"
class Z {

  void z() {
    int i = 0;
    if (true) (<caret>i < 100) ? i++ : i--
  }
}