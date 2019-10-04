import java.util.*;

class Test1 {
  private void foo() {
    compute(true);
  }

  private void bar() {
    compute(false);
  }

  private void comp<caret>ute(boolean mode) {
    for(int i=0; i<10; i++) {
      if (i % 2 == mode) {
        System.out.println("foo");
      }
      if (mode) {
        System.out.println("bar");
      } else {
        System.out.println("baz");
      }
    }
    System.out.println(mode);
    Set<String> set = new HashSet<>();
    if (set.add(foo) && mode) {
      System.out.println("hello");
    } else {
      System.out.println("goodbye");
    }
  }
}