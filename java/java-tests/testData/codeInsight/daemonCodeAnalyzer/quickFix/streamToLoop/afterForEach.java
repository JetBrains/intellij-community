// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;

public class Main {
  private static void test(List<String> list) {
      for (String x : list) {
          if (x != null) {
              System.out.println(x);
          }
      }
  }

  public static void main(String[] args) {
    test(Arrays.asList("a", "b", "xyz"));
  }

  void testLocal() {
    class X extends ArrayList<String> {
      class Y {
        void test() {
            for (String s : X.this) {
                System.out.println(s);
            }
        }
      }
    }
  }

  void testAnonymous() {
    new ArrayList<String>() {
      class Y {
        void test() {
            for (Iterator<String> it = stream().iterator(); it.hasNext(); ) {
                String s = it.next();
                System.out.println(s);
            }
        }
      }
    };
  }
}