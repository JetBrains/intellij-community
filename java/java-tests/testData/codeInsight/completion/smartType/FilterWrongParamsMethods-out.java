import java.util.ArrayList;
import java.util.List;

class A {
  {
    List<A> s = new ArrayList<>();
    s.stream().filter(A::accept);
  }

  static <K> boolean accept(K k) {
    return false;
  }

  public boolean accept1(String s) {
    return true;
  }
}

