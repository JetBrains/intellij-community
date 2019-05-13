// "Change variable 'list' type to 'List<java.lang.Integer>'" "true"
public class Test {
  void foo()  {
    List<Integer> list = new List<>();
    list.add(new Integer(0));
  }
}

class List<T> {
  void add(T t){}
}