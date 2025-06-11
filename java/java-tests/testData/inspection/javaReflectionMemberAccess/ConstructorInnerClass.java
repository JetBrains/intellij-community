class Constructors {
  class X {
    X() {}
    X(String a) {}
  }

  public void testConstructor() throws Exception {
    X.class.getDeclaredConstructor<warning descr="Cannot resolve constructor with specified argument types">()</warning>;
    X.class.getDeclaredConstructor<warning descr="Cannot resolve constructor with specified argument types">(String.class)</warning>;

    X.class.getDeclaredConstructor(Constructors.class);
    X.class.getDeclaredConstructor(Constructors.class, String.class);
  }
}