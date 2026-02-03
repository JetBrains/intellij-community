import java.util.ArrayList;
import java.util.List;

class A {
  {
    List<A> s = new ArrayList<>();
    s.stream().filter(A::accept);
  }

  public boolean accept() {
    return true;
  }
}

