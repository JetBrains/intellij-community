public class NonConstant {
  void m(String s) {
    String a = "ab" + get();
    s = s.replaceAll(a, "y");
  }

  String get() { return "c"; }
}