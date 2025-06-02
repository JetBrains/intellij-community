class Safe {
  public String <caret>x(int i) {
    return "yes";
  }

  static void main() {
    final Safe safe = new Safe();
    IntFunction<String> f = safe::x;
  }
}