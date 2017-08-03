// "Replace 'switch' with 'if'" "true"
class X {

  void m(String s, int a) throws IOException {
    switch<caret> (s) {
      case "a": {
        a();
        break;
      }
      default: {
        d();
      }
    }
  }

  void a() throws IOException { }
  void d() throws IOException { }
}