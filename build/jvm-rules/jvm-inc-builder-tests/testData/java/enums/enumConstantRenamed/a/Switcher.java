public class Switcher {
  int f(E e) {
    return switch (e) {
      case FOO -> 1;
      case BAR -> 2;
    };
  }
}
