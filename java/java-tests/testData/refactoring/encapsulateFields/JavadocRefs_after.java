class A {
  protected int i;

    public int getI() {
        return i;
    }

    public void setI(int i) {
        this.i = i;
    }
}

class B extends A {
  /**
   * {@link #i}
   */
  public int getI() {
    return 0;
  }

  {
    System.out.println(super.getI());
  }
}
