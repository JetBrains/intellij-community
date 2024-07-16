import java.util.*;

class Main {
  public static void main(String[] args) {
    var lc = (<warning descr="Casting 'asLowerCase(...)' to 'String' is redundant">String</warning>)asLowerCase("Test");
    System.out.println(lc);
    var lc0 = (List<?>)Collections.emptyList();
    System.out.println(lc0);
    var lc1 = (CharSequence)asLowerCase("Test");
    System.out.println(lc1);
    String lc2 = (<warning descr="Casting 'asLowerCase(...)' to 'String' is redundant">String</warning>)asLowerCase("Test");
    System.out.println(lc2);
  }
  private static String asLowerCase(String str) {
    return str.toLowerCase();
  }
}