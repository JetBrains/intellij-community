// "Replace method reference with lambda" "true-preview"
public class Test {
  void test(){
    class Local(){}
    Supplier<Local> supplier = () -> new Local();
  }
}

interface Supplier<T> {
  T get();
}