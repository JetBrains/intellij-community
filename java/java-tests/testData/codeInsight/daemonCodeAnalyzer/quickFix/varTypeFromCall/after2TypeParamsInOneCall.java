// "Change variable 'list' type to 'Lost<String, Integer>'" "true"
public class Test {
  void foo()  {
    Lost<String, Integer> list = new Lost<>();
    list.add("", new Integer(42));
  }
}

class Lost<T, K> {
  void add(T lt, K t){}
}