class A {
  protected int i;
}

class B extends A {
  /**
   * {@link #i}
   */
  public int getI() {
    return 0;
  }

  {
    System.out.println(i);
  }
}
