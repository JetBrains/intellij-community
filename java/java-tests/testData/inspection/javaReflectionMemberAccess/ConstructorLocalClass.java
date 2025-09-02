class Constructors {
  public void testConstructor() throws Exception {
    class X {
      X() {}
      X(String a) {}
    }

    X.class.getDeclaredConstructor<warning descr="Cannot resolve constructor with specified argument types">()</warning>;
    X.class.getDeclaredConstructor<warning descr="Cannot resolve constructor with specified argument types">(String.class)</warning>;

    X.class.getDeclaredConstructor(Constructors.class);
    X.class.getDeclaredConstructor(Constructors.class, String.class);
  }
}