// "Change variable 'list' type to 'Lost<Integer>'" "true"
public class Test {
  void foo()  {
    Lost<Integer> list = new Lost<>();
    list.addd(new Lost<Integer>(), new Integer(9));
  }
}

class Lost<T> {
  void addd(Lost<T> lt, T t){}
}