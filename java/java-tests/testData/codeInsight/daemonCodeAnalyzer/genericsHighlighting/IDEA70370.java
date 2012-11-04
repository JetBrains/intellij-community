class Devk1 {
  public void main(String args[]) {
    foo();
  }

  private void <warning descr="Private method 'foo(java.lang.Object...)' is never used">foo</warning>(Object... objects) {
    System.out.println("OBJECTS" + objects);
  }

  private void foo(int... ints) {
    System.out.println("INTS" + ints);
  }
}
