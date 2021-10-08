
class Main {
  private static final int LEVEL = 0;
  int f(Object o) {
    return switch(o) {
        case LEVEL -> <caret>
    }
  }
}