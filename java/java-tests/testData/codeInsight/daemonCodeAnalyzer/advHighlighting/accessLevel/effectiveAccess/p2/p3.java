package effectiveAccess.p2;

public class p3 extends p2 {
}

<error descr="Class 'p4' must either be declared abstract or implement abstract method 'f()' in 'pp'">class p4 extends effectiveAccess.pp</error> {
  public void f() {}
}

class pderived extends p2 {
  protected void f() {
    super.f();
  }
  protected void v() {
    super.v();
  }
} 
