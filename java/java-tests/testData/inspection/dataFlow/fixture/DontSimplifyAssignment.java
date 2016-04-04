class Contracts {

  boolean x;

  public boolean testSideEffect() {
    return x && <warning descr="Condition '!(x = false)' is always 'true' when reached">!(x =<caret> false)</warning>;
  }

  public void testSideEffectInsideCall() {
    if (a()) {
      System.out.println();
    }
  }

  private boolean a() {
    return x = false;
  }

}