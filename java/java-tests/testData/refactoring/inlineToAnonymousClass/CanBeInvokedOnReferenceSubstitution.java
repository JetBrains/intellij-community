public class Simple<T> implements Comparable<T> {
  public int compareTo(T o){}
}

class Usage {
  void foo() {
    bar(new Si<caret>mple<String>());
  }

  void bar(Comparable<String>... r){}
}