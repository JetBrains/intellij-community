class Constructors {
  public static void testConstructorInStatic() throws Exception {
		class X {
			X(int i) {}
			class Y {
        Y() {}
        Y(String a) {}
			}
		}

    X.class.getDeclaredConstructor(int.class);
		X.Y.class.getDeclaredConstructor(X.class);
    X.Y.class.getDeclaredConstructor(X.class, String.class);
	}
}