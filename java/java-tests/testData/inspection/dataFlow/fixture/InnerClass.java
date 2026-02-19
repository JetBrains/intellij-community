import java.util.*;

class InnerClassTest {
  class Inner {}

  public static void main(String[] args) {
    InnerClassTest test = Math.random() > 0.5 ? new InnerClassTest() : null;

    System.out.println(test.new <warning descr="Inner class construction may produce 'NullPointerException'">Inner</warning>());
  }
  
  void dimensionNotInnerClass() {
    Integer x = null;
    int[] data = new int[<warning descr="Unboxing of 'x' may produce 'NullPointerException'">x</warning>];
  }
}