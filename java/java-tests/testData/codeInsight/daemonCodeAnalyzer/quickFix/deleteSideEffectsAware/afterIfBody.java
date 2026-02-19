// "Extract side effects as an 'if' statement" "true-preview"
class Z {

  void z() {
    int i = 0;
    if (true) {
        if ((i < 100)) {
            i++;
        } else {
            i--;
        }
    }
  }
}