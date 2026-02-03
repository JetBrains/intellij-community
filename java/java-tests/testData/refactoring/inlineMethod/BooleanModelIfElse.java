class Test {

  private boolean test(String s) {
    if(s != null) {
      s = s.trim();
      if (s.isEmpty()) {
        return true;
      }
    }
    return false;
  }

  String use(String s) {
    if(<caret>test(s+s)) throw new IllegalArgumentException();
    else return "nice";
  }
}