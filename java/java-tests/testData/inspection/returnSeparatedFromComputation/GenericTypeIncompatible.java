import java.util.*;

class T {
  List<Object> f(boolean b) {
    List raw = null;
    if (b) {
      raw = g();
    }
    return raw;
  }

  List<String> g() {
    return Collections.singletonList("");
  }
}