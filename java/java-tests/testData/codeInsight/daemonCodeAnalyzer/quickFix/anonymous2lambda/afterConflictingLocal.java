// "Replace with lambda" "true"
class X1 {
  Runnable m() {
    String s;
    return () -> {
      String s1;
    };
  }
}