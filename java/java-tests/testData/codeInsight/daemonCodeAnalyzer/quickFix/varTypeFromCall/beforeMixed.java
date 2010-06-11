// "Change 'list' type to 'Lost<java.lang.Integer>'" "true"
public class Test {
  void foo()  {
    Lost<String> list = new Lost<String>();
    list.add(new Lost<Integ<caret>er>(), new Integer(42));
  }
}

class Lost<T> {
  void add(Lost<T> lt, T t){}
}