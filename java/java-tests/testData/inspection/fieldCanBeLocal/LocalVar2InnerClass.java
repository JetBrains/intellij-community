<error descr="Class 'Outer' is public, should be declared in a file named 'Outer.java'">public class Outer</error> {
  private int <warning descr="Field can be converted to a local variable">value</warning> = 0;

  public class Inner {
    private final int myValue;

    public Inner() {
      myValue = value;
    }
  }
}