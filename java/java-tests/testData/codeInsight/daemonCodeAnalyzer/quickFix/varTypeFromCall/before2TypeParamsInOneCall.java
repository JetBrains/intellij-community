// "Change 'list' type to 'Lost<java.lang.String,java.lang.Integer>'" "true"
public class Test {
  void foo()  {
    Lost<String, String> list = new Lost<String, String>();
    list.add("", new Int<caret>eger(42));
  }
}

class Lost<T, K> {
  void add(T lt, K t){}
}