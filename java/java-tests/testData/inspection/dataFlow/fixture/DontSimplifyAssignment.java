class Contracts {

  boolean x;

  public boolean testSideEffect() {
    return x && <warning descr="Condition '!(x = false)' is always 'true' when reached">!(x =<caret> false)</warning>;
  }

}