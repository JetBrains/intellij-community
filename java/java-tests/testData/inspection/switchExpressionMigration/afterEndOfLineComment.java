// "Fix all 'Statement can be replaced with enhanced 'switch'' problems in file" "true"
import java.util.*;

class EndOfLineComment {
  public static String m(int x) {
      return switch (x) {
          case 0 -> "foo";
          default -> {
              System.out.print("bar"); // !
              yield "bar";
          }
      };
  }
}