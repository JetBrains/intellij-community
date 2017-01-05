// "Move 'return' closer to computation of the value of 'raw'" "false"
import java.util.*;

class T {
  List<Object> f(boolean b) {
    List raw = null;
    if (b) {
      raw = g();
    }
    re<caret>turn raw;
  }

  List<String> g() {
    return Collections.singletonList("");
  }
}