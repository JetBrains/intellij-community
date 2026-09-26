import java.util.Collection;
import java.util.ArrayList;

class X {
  public static void main(String[] args) {
    Collection<Integer>[] a = new Collection[2];
    for (int t = 0; t < 2; t++) a[t] = new ArrayList<>();
    for (int i = 0; i < 4; i++) a[i % 2].add(i);
    if (a[0].isEmpty() || a[1].isEmpty()) {
      System.out.println("never happens");
    }
  }
}