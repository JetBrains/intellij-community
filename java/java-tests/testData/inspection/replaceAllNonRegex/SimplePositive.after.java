public class SimplePositive {
  void m(String s) {
    s = s.replace("abc", "x");
    s = s.replace("-", "/");
    s = s.replace(" ", "_");
    s = s.replace("]", "a");
  }
}