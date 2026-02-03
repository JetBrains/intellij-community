public class Simple<T> implements Comparable<T> {
  public int compareTo(T o){}
}

class Usage<S> {
  void foo() {
    bar(new Si<caret>mple<S>());
  }

  void bar(Comparable<S>... r){}
}