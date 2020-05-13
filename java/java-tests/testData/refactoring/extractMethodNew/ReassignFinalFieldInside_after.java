class X {
  private final int i;

  public X() {

      i = newMethod();

  }

    private int newMethod() {
        final int i;
        i = 0;
        System.out.println(i);
        return i;
    }
}
