// "Change variable 'list' type to 'List<Integer>'" "true"
public class Test {
  void foo()  {
    List<String> list = new List<String>();
    list.add(4<caret>2);
  }
}

class List<T> {
  void add(T t){}
}