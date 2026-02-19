// "Fix all 'Statement can be replaced with enhanced 'switch'' problems in file" "true"
import java.util.*;

class CommentsInTheMiddle {
  public static String m(int x) {
      return switch (x) {
          case 0 ->
              // first strange comment
                  "foo";
          default -> {
              // some strange comment
              System.out.print("bar");
              // another strange comment
              yield "bar";
          }
      };
  }
}