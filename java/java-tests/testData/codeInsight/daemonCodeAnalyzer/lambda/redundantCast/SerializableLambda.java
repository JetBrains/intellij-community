
import java.io.Serializable;

class Test {

  public static void main(String[] args) {
    Runnable r = (Runnable & Serializable) (() -> {});
    r = (Runnable & Serializable)() -> {};
    r = (<warning descr="Casting '() -> {}' to 'Runnable & I' is redundant">Runnable & I</warning>)() -> {};
    System.out.println(r);
  }
  
  interface I {}
}