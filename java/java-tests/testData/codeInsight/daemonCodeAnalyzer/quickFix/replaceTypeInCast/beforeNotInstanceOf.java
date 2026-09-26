// "Fix all 'Constant conditions & exceptions' problems in file" "false"
import java.util.*;

class X {
  void test(Object x) {
    if (!(x instanceof Number)) {
      System.out.println(((<caret>Integer)x).longValue());
    }
  }
}