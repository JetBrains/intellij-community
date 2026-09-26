// "Invert 'if' condition" "true"
class D {
  private void f(char[] buffer) {
    for (char c : buffer) {
      if (true)<caret> {
        break;
      } // THIS COMMENT DISAPPEARS
    }
  }
}