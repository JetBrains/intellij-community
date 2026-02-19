class Aucontraire {
  private Inner b = new <caret>Inner(1, 2); // inline this call

  private class Inner {
    public Inner(String s, int... i) {
    }

    public Inner(int... i) {
      this("", i);
    }

    public String toString() {
      return "A";
    }
  }
}