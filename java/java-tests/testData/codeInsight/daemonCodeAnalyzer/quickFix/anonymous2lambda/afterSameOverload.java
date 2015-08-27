// "Replace with lambda" "true"
import java.util.concurrent.Callable;

class A {
  static void submit(Runnable r){}

  static <T> T submit(Callable<T> c){
    return null;
  }

  public static void main(String[] args) {
    submit((Runnable) () -> new A());
  }
}