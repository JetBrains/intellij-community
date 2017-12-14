import java.util.*;

enum En {
  A(new Y<>((<warning descr="Casting 'new ArrayList<>()' to 'List' is redundant">List</warning>) new ArrayList<>()));

  En(Y<List<Object>> list) {
  }
}

class Y<I> {
  public Y(I e) {
  }
}