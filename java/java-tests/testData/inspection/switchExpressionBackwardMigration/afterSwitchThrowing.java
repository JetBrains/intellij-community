// "Replace with old style 'switch' statement" "true"
import java.util.*;

public class GenerateThrow {
  void foo(int i) {
      int res;
      switch (i) {
          case 0:
              res = 1;
              break;
          default:
              throw new IllegalArgumentException();
      }
  }
}