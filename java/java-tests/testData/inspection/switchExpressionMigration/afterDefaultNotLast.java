// "Replace with 'switch' expression" "true-preview"

class X {
  enum State {
    CANCELLED, INTERRUPTING, NORMAL
  }

  String test(State state, String outcome) {
      return switch (state) {
          default -> "1";
          case CANCELLED, INTERRUPTED -> "2";
      };
  }
}