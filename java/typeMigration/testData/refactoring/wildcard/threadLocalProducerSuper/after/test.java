import java.util.*;

class Test {
  void method(ThreadLocal<List<? super String>> l) {
    l.get().add("");
  }
}