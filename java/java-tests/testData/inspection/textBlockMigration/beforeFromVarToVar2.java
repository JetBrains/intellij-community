// "Replace with text block" "true-preview"
class Main {
  String foo(String s) {
    return s + "(QWERTYUIOP<caret>\n" +
           "ASDFGHJLKZ\n" +
           "ZVNHGLMLSK\n" +
           ")" + s + s;
  }
}