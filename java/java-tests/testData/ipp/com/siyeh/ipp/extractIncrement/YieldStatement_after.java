class Main {
  int test(int i) {
    return switch(i) {
      default -> {
          int j = i;
          i++;
          yield <caret>j;
      } 
    };
  }
}