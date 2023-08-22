// "Remove local variable 'i'" "true-preview"
public class Main {
  int test(String s) {
      return switch(s) {
      default -> 1;
    }
  }
}