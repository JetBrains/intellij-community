// "Merge with previous 'map()' call" "false"

import java.util.function.Function;
import java.util.stream.Stream;

public class Main {
  interface MyFunction extends Function<Object, Boolean> {
    default boolean apply(String s) {return false;}
  };

  public static void main(String[] args) {
    MyFunction fn = "xyz"::equals;
    System.out.println(Stream.of("xyz").map(fn).an<caret>yMatch(Boolean::booleanValue));
  }
}