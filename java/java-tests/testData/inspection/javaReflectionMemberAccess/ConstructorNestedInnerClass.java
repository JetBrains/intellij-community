class Constructors {
  class X {
    class Y {
      Y() {}
      Y(String a) {}
    }
  }

  public void testConstructor() throws Exception {
    X.Y.class.getDeclaredConstructor(X.class);
    X.Y.class.getDeclaredConstructor(X.class, String.class);
  }
}