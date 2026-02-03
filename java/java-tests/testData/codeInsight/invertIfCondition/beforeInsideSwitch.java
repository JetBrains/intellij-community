// "Invert 'if' condition" "true"
class A {
  public boolean accept() {
    boolean xx = true;
    switch (1) {
      case 0:
        <caret>if (xx) {
          return true;
        }
        break;
    }
    return false;
  }
}