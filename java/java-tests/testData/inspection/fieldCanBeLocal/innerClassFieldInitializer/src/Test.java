public class Outer {
  private int value = 0;

  public class Inner {
    private final int myValue = Outer.this.value;
  }
}