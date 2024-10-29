// "Invert 'if' condition" "true"
class D {
  private void f(char[] buffer) {
    for (char c : buffer) {
        if (false) {
            continue; // THIS COMMENT DISAPPEARS
        }
        break;
    }
  }
}