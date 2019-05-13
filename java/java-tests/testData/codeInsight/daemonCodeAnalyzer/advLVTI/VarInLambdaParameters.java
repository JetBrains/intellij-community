import java.util.function.Function;

class C {
  void m() {
    Function<String, String> f1 = (var var) -> var;
    Function<String[], String> f2 = (<error descr="'var' is not allowed as an element type of an array">var</error> arr[]) -> "";
    Function<String[], String> f3 = (var arr) -> "";
  }
}