// "Replace 'switch' with 'if'" "true"
class X {

  void m(String s, int a) throws IOException {
      if ("a".equals(s)) {
          a();
      } else {
          d();
      }
  }

  void a() throws IOException { }
  void d() throws IOException { }
}