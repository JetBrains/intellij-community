// "Change variable 'list' type to 'Lost<Integer>'" "true"
public class Test {
  void foo()  {
    Lost<String> list = new Lost<String>();
    list.add(new Lost<Integ<caret>er>());
  }
}

class Lost<T> {
  void add(Lost<T> t){}
}