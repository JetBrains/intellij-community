// "Replace method reference with lambda" "true-preview"
public class Test {
  void test(){
    class Local(){}
    Supplier<Local> supplier = Local:<caret>:new;
  }
}

interface Supplier<T> {
  T get();
}