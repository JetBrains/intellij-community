class NotEqual {

  void colors() {
    String[] input = new String[] { "Color.WHITE", "Color.GREEN" };
    String[] copy = new String[input.length];

      // Manual array copy
      System.arraycopy(input, 0, copy, 0, input.length);
  }
}