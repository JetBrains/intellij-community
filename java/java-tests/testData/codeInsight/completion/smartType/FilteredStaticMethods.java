import java.util.ArrayList;
import java.util.List;

class A {
  {
    List<A> s = new ArrayList<>();
    s.stream().filter(A::<caret>);
  }

  public boolean accept() {
    return true;
  }
}

