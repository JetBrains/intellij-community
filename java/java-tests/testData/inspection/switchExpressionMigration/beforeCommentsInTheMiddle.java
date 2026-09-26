// "Fix all 'Statement can be replaced with enhanced 'switch'' problems in file" "true"
import java.util.*;

class CommentsInTheMiddle {
  public static String m(int x) {
    switc<caret>h(x) {
      case 0:
        // first strange comment
        return "foo";
      default:
        // some strange comment
        System.out.print("bar");
        // another strange comment
        return "bar";
    }
  }
}