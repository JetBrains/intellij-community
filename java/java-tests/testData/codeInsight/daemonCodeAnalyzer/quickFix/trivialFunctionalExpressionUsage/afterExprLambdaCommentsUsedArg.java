// "Replace method call on lambda with lambda body" "true-preview"

import java.util.function.Function;

public class Test {
  public static void main(String[] args) {
      /* bar */
      String s = "a" +/* who-hoo */ "x" + "foo";
  }
}