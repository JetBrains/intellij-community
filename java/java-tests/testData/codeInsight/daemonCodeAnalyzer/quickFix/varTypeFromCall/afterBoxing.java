// "Change variable 'list' type to 'List<Integer>'" "true"
public class Test {
  void foo()  {
    List<Integer> list = new List<>();
    list.add(42);
  }
}

class List<T> {
  void add(T t){}
}