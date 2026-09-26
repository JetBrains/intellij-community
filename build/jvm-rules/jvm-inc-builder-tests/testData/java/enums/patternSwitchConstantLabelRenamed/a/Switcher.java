public class Switcher {
  int f(E e) {
    return switch (e) {
      case null -> -1;
      case BAR -> 1;
      default -> 0;
    };
  }
}
