class NotEqual {

  void colors() {
    String[] input = new String[] { "Color.WHITE", "Color.GREEN" };
    String[] copy = new String[input.length];

    for<caret> (int i = 0; i != input.length; ++i) { // Manual array copy
      copy[i] = input[i];
    }
  }
}