import java.util.*;

class InnerClassTest {
  class Inner {}

  public static void main(String[] args) {
    InnerClassTest test = Math.random() > 0.5 ? new InnerClassTest() : null;

    System.out.println(test.new <warning descr="Inner class construction may produce 'java.lang.NullPointerException'">Inner</warning>());
  }
}