public class Switcher {
  int f(A a) {
    return switch (a) {
      case B b -> 1;
      case C c -> 2;
    };
  }
}
