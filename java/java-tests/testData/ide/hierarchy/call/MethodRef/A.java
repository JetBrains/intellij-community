
import static java.util.Arrays.asList;

class A {
  public static String returnAString(String anotherString) {
    return anotherString;
  }

  public static void testMethod() {
    asList("abcd").stream().map(A::returnAString);
  }
}