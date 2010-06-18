// "Change 'list' type to 'Lost<java.lang.String,java.lang.Integer>'" "true"
public class Test {
  void foo()  {
    Lost<String, Integer> list = new Lost<String,Integer>();
    list.add("", new Integer(42));
  }
}

class Lost<T, K> {
  void add(T lt, K t){}
}