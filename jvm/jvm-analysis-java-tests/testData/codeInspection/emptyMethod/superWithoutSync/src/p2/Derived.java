package p2;
import p1;
public class Derived extends p1.Base {
  public synchronized void foo() {
     super.foo();
  }
}