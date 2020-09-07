class a {
  void foo(String s, int i, boolean b) {
    String desc = "Our " + s + " has " + (b ? i + " doo<caret>rs" : "many windows");
  }
}