// "Change 'list' type to 'Lost<java.lang.Integer>'" "true"
public class Test {
  void foo()  {
    Lost<String> list = new Lost<String>();
    list.addd(new Lost<Integer>(), new Int<caret>eger(9));
  }
}

class Lost<T> {
  void addd(Lost<T> lt, T t){}
}