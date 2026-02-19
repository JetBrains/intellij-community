// "Add private no-args constructor to X" "true-preview"
class Z {
  private class X {
    X(int... a) {}
    X(String... b) {}
  }

  class Y<caret> extends X {
  }
}