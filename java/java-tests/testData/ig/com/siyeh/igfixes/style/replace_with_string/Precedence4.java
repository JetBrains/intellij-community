

class Precedence4 {

  void f(boolean b) {
    StringBuilder sb<caret> = new StringBuilder(b ? "sub" : "super");
    sb.append("script");
    String string = sb.toString();
  }
}