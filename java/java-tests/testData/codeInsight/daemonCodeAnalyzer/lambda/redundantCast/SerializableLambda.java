
import java.io.Serializable;

class Test {

  public static void main(String[] args) {
    Runnable r = (Runnable & Serializable) (() -> {});
    r = (Runnable & Serializable)() -> {};
    r = (Runnable & I)() -> {};
    System.out.println(r);
    
    
    J j = (<warning descr="Casting '() -> {...}' to 'J' is redundant">J</warning>) () -> {};
    J j1 = (J1) () -> {};
  }
  
  interface I {}
  interface J extends Serializable {
    void m();
  }
  interface J1 extends J {}
}