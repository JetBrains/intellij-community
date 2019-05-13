
import java.io.Serializable;

class Test {

  public static void main(String[] args) {
    Runnable r = (Runnable & Serializable) (() -> {});
    r = (Runnable & Serializable)() -> {};
    r = (Runnable & I)() -> {};
    System.out.println(r);
  }
  
  interface I {}
}