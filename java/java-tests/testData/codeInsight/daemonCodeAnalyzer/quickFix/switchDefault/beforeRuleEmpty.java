// "Unwrap 'switch'" "true"
public class One {
  void n() {
    if (true) {
      swit<caret>ch (0) {
        default -> {
        }
      }
    }
  }
}