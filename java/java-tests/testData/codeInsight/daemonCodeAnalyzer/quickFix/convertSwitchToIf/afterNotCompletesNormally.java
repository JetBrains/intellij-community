// "Replace 'switch' with 'if'" "true-preview"
class X {

  void m(String s, int a) throws IOException {
      if (s.equals("a")) {
          a();
      } else {
          d();
      }
  }

  void a() throws IOException { }
  void d() throws IOException { }
}