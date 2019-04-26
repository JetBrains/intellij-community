<error descr="Class 'Outer' is public, should be declared in a file named 'Outer.java'">public class Outer</error> {
  private int value = 0;

  public class Inner {
    private final int myValue = Outer.this.value;
  }
}