// "Replace with text block" "INFORMATION-preview"
class Main {
  String foo(String s) {
    return s + "(QWERTYUIOP<caret>\n" +
           "ASDFGHJLKZ\n" /*blah blah blah*/ +
           "ZVNHGLMLSK\n" +
           ")" + s + s;
  }
}