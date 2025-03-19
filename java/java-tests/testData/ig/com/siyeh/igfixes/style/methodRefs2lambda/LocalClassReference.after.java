public class Test {
  void test(){
    class Local(){}
    Supplier<Local> supplier = () -> new Local();
  }
}

interface Supplier<T> {
  T get();
}