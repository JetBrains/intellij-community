import java.util.*;

class Unboxing {

  void m(List<Integer> sheep) {
      for (int i : sheep) {
          if (i == Integer.valueOf(10000)) {
              throw null;
          }
      }
  }
}