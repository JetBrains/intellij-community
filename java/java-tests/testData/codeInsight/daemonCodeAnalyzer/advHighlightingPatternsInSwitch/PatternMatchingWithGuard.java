
class Main {
  static int m(Object o) {

    int i1 = switch(o) {
      case String s when s.length() > 0 -> s.length();
      case String s -> s.length();
      default -> 1;
    };

    int i2 = switch(o) {
      case String s when (s.length() > 0) -> s.length();
      case String s -> s.length();
      default -> 1;
    };

    if (o instanceof String s && s.length() > 0) {}
    if (o instanceof String s && (s.length() > 0)) {}

    return i1 + i2;
  }
}