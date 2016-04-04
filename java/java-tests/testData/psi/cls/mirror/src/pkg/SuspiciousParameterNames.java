package pkg;

public class SuspiciousParameterNames {
  public @interface A { }

  public void m1(int p1, @A int p2) { }
  public void m2(int x, @A int y) { }
}