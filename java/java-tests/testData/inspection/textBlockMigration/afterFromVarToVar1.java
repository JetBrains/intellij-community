// "Replace with text block" "true-preview"
class Main {
  String foo(String s) {
    return "123456" + s + s + """
            (QWERTYUIOP
            ASDFGHJLKZ
            ZVNHGLMLSK
            )""" + s + "7890";
  }
}