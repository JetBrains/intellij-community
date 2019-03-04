// "Remove variable 'i'" "true"
public class Main {
  int test(String s) {
      return switch(s) {
      default -> 1;
    }
  }
}