// "Replace with text block" "INFORMATION-preview"
class Main {
  String foo(String s) {
    return s + """
            (QWERTYUIOP
            ASDFGHJLKZ
            ZVNHGLMLSK
            )""" + s + s;
  }
}