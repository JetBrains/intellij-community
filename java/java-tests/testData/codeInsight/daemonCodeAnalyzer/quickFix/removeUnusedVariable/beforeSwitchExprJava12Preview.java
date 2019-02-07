// "Remove variable 'i'" "true"
public class Main {
  int test(String s) {
    int <caret>i;
    return switch(s) {
      default -> i = 1;
    }
  }
}