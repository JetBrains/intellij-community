class Test {

  private boolean test(String s) {
    if (s == null) return false;
    s = s.trim();
    if (s.isEmpty()) return false;
    return true;
  }

  void use(String s) {
    if(!<caret>test(s+s)) return;
    System.out.println("woohoo");
  }
}