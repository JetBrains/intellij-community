import java.util.ArrayList;

public class NestedUnrolledLoopNotComplex {
  public static void <weak_warning descr="Method 'main' is complex: data flow results could be imprecise">main</weak_warning>(String[] args) {
    ArrayList<Integer> x = new ArrayList<>();
    ArrayList<Integer> y = new ArrayList<>();
    for (int i = -1; i < 2; i++) {
      for (int j = -1; j < 2; j++) {
        if (contains(i, j)) {
          x.add(i);
          y.add(j);
        }
      }
    }
    if (x.size() < 10) {}
    if (y.size() < 10) {}
  }

  static native boolean contains(int x, int y);
}
