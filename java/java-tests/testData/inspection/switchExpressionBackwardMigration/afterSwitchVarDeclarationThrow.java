// "Replace with old style 'switch' statement" "true"
import java.util.*;

public class GenerateThrow {
  void foo(int i) {
      int res;
      switch (i) { // convert to 'old style' switch
          case 0:
              res = 1;
              break;
          /*1*/
          /*2*/
          default:
              throw /*3*/new IllegalArgumentException();
      }
  }
}