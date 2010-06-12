// "Change 'list' type to 'Lost<java.lang.Integer>'" "true"
public class Test {
  void foo()  {
    Lost<Integer> list = new Lost<Integer>();
    list.addd(new Lost<Integer>(), new Integer(9));
  }
}

class Lost<T> {
  void addd(Lost<T> lt, T t){}
}