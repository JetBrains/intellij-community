// "Add private no-args constructor to X" "true"
class Z {
  private class X {
    X(int... a) {}
    X(String... b) {}

      private X() {
      }
  }

  class Y extends X {
  }
}