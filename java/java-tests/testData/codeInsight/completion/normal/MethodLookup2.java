class A {
  int getAAA(int param) { return 0; }
  public void actionPerformed(A a) {
    a.getAA<caret>
    ((A)a).actionPerformed(this);
  }
}