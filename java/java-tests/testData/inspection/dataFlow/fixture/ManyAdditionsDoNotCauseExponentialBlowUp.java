import java.util.*;

class Test {
  native boolean get();

  void test() {
    int x = get() ? 0 : 1<<30;
    x = x + (get() ? 0 : 1<<29);
    x = x + (get() ? 0 : 1<<28);
    x = x + (get() ? 0 : 1<<27);
    x = x + (get() ? 0 : 1<<26);
    x = x + (get() ? 0 : 1<<25);
    x = x + (get() ? 0 : 1<<24);
    x = x + (get() ? 0 : 1<<23);
    x = x + (get() ? 0 : 1<<22);
    x = x + (get() ? 0 : 1<<21);
    x = x + (get() ? 0 : 1<<20);
    x = x + (get() ? 0 : 1<<19);
    x = x + (get() ? 0 : 1<<18);
    x = x + (get() ? 0 : 1<<17);

    if (<warning descr="Condition 'x < 0' is always 'false'">x < 0</warning>) {
      System.out.println("Impossible");
    }
  }
}
