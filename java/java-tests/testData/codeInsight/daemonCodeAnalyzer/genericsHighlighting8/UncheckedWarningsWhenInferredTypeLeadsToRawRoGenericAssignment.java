import java.util.ArrayList;

class Test {

  static <T extends ArrayList<String>> T foo() {return null;}

  static class Raw extends ArrayList {}

  public static void main(String[] args) {
    Raw r = <warning descr="Unchecked method 'foo()' invocation">foo</warning>();
    System.out.println(r);
  }
}