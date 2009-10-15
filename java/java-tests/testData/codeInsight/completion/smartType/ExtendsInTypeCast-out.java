class List<T> {
  public void add(T t) {};
}

public class A {
  {
    List<? extends Object> list;
    list.add((Object) <caret>)
  }
}
