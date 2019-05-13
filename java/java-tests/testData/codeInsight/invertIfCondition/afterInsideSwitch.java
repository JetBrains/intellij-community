// "Invert 'if' condition" "true"
class A {
  public boolean accept() {
    boolean xx = true;
    switch (1) {
      case 0:
          if (!xx) {
              break;
          }
          else {
              return true;
          }
    }
    return false;
  }
}