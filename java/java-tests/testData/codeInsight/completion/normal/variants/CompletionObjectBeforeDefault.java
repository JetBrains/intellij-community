public class Main {
  boolean x(Object e) {
    return switch (e) {
      ca<caret>
      default -> false;
    };
  }
}