import java.util.Arrays;

class Test {
  public static void main(String[] args) {
    System.out.println(use(new Object[]{"1", "2", "3"}));
  }

  public static String use(Object[] arr<caret>) {
    return Arrays.toString(arr);
  }
}