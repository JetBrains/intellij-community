public class Main {
  boolean x(Object e) {
    return switch (e) {
      default -> false;
      ca<caret>
    };
  }
}