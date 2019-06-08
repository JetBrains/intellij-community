// "Replace with 'switch' expression" "true"
import java.util.*;

public class Test {
  enum Size { S, M, L };
  int calcHeight(Size size) {
    int h = switch (size) {
        case S -> 12;
        case M -> 24;
        case L -> 36;
        default -> -1;
    };
      return h;
  }
}