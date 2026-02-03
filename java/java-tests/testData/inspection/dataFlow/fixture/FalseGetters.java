class A {
  private String s;
  private int next = 0;

  public A(final String s) {
    this.s = s;
  }

  private char getChar() {
    return s.charAt(next++);
  }

  private void foo() {
    char c = getChar();
    if (c == 'a') {
      if (getChar() == 'b') {
        System.out.println("ab");
      }
    }
  }
}