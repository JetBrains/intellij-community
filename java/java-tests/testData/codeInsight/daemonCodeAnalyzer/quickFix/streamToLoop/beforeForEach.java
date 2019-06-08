// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;

public class Main {
  private static void test(List<String> list) {
    list.stream().filter(x -> x != null).for<caret>Each(y -> System.out.println(y));
  }

  public static void main(String[] args) {
    test(Arrays.asList("a", "b", "xyz"));
  }

  void testLocal() {
    class X extends ArrayList<String> {
      class Y {
        void test() {
          stream().forEach(System.out::println);
        }
      }
    }
  }

  void testAnonymous() {
    new ArrayList<String>() {
      class Y {
        void test() {
          stream().forEach(System.out::println);
        }
      }
    };
  }
}