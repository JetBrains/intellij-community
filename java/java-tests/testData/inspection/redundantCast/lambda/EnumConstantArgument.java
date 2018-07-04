import java.util.*;

enum En {
  A(new Y<>((List) new ArrayList<>()));

  En(Y<List<Object>> list) {
  }
}

class Y<I> {
  public Y(I e) {
  }
}