// "Change 'list' type to 'List<java.lang.Integer>'" "true"
public class Test {
  void foo()  {
    List<String> list = new List<String>();
    list.add(4<caret>2);
  }
}

class List<T> {
  void add(T t){}
}