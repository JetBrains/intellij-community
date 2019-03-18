// "Replace with 'switch' expression" "true"
import java.util.*;

public class Test {
  enum Size { S, M, L };
  int calcHeight(Size size) {
    int h = -1;
    switch<caret> (size) {
      case S:
        h = 12;
        break;
      case M:
        h = 24;
        break;
      case L:
        h = 36;
        break;
    }
    return h;
  }
}