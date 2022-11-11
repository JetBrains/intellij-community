// "Replace with lambda" "true-preview"
class X1 {
  Runnable m() {
    String s;
    return () -> {
      String s1;
    };
  }
}