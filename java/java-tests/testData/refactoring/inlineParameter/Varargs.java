import java.util.Arrays;

class Test {
  public static void main(String[] args) {
    System.out.println(use("1", "2", "3"));
  }

  public static String use(String... os<caret>) {
    x(os);
    return Arrays.toString(os);
  }
  
  static void x(Object... os) {
    for (Object o : os) {
      System.out.println(o);
    }
  }
}