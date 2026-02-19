class Main {
  final int i = 2;

  String test(Object obj) {
    return switch (obj) {
      case i<caret>, Integer i -> "hello";
        default -> "nothing";
    }
  }
}