class Switching {
  boolean x(int i) {
    switch (i) {
      case 1 -> {<caret>
        return true;
      }
      default -> {
        return false;
      }
    }
  }
}