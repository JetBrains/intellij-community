public class Test {
  void test(){
    class Local(){}
    Supplier<Local> supplier = Local:<caret>:new;
  }
}

interface Supplier<T> {
  T get();
}