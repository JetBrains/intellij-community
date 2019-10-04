// "Change variable 'list' type to 'Lost<Integer>'" "true"
public class Test {
  void foo()  {
    Lost<Integer> list = new Lost<>();
    list.add(new Lost<Integer>());
  }
}

class Lost<T> {
  void add(Lost<T> t){}
}