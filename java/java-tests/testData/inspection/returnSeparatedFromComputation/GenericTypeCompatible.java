import java.util.*;

class T {
  List<String> f(boolean b) {
    List raw = null;
    if (b) {
      raw = g();
    }
    <warning descr="Return separated from computation of value of 'raw'">return</warning> raw;
  }

  List<String> g() {
    return Collections.singletonList("");
  }
}