// "Replace with enhanced 'switch' statement" "true-preview"
import java.util.*;

class CommentsInside {

  void test(int x) {
    sw<caret>itch (x) {
      case 0:
        // nothing to do
        break;
      case 1:
        System.out.println(x);
    }
  }
}