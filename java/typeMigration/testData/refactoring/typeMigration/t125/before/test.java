import java.util.*;

class Test {
  public <U> List<U> meth(U p) {
    return Collections.singletonList(p);
  }
}