// "Change 'list' type to 'List<java.lang.Integer>'" "true"
public class Test {
  void foo()  {
    List<String> list = new List<String>();
    list.add(new In<caret>teger(0));
  }
}

class List<T> {
  void add(T t){}
}