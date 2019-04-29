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

  void use(String s) {
    if(<caret>test(s+s)) throw new IllegalArgumentException();
    System.out.println("woohoo");
  }
}