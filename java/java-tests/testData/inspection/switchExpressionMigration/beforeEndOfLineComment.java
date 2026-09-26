// "Fix all 'Statement can be replaced with enhanced 'switch'' problems in file" "true"
import java.util.*;

class EndOfLineComment {
  public static String m(int x) {
    swit<caret>ch (x) {
      case 0:
        return "foo";

      default:
        System.out.print("bar"); // !
        return "bar";
    }
  }
}