class Outer {
  private int value = 0;

  public class Inner {
    private final int <warning descr="Field can be converted to a local variable">myValue</warning>;

    public Inner() {
      myValue = value++;
    }
  }
}