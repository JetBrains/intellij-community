// "Replace with old style 'switch' statement" "true"
import java.util.*;

class X {
  boolean x(int i) {
    boolean b;
      switch (i) {
          case 2:
              throw null;
          default:
              b = true;
              break;
      }
    return b;
  }
}