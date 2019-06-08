// "Simplify" "true"
class Test {
  public static void main(String[] args) {
    foo(false ? <caret>"" : null, (int) 0);
  }
  
  static void foo(String s, int i) {}
  static void foo(Number n, int i) {}
}