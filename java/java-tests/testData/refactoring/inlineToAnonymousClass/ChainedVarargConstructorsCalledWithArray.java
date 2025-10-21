class Absolutely {
  private Inner b = new Inner(new int[]{1, 2});

  private class Inner<caret> { // inline here
    public Inner(String s, int... i) {
      System.out.println(i);
    }

    public Inner(int... i) {
      this("", i);
    }

    public String toString() {
      return "A";
    }
  }
}