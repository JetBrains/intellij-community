// "Replace method call on lambda with lambda body" "true"

import java.util.function.Function;

public class Test {
  public static void main(String[] args) {
    String s = ((Function<String, String>)((x) -> /* bar */ x + "foo")).a<caret>pply("a" +/* who-hoo */ "x");
  }
}