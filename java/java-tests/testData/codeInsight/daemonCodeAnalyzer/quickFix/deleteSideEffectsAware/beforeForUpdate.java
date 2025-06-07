// "Extract side effects as an 'if' statement" "false"
class Z {

  void z() {
    for (int i = 0; ; (i < 100) ?<caret> i++ : i--) {
    }
  }
}