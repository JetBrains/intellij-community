// "Replace 'if else' with '?:'" "INFORMATION"
class OverwrittenDeclaration {

  void x(Object t) {
    int x = 0;
    if<caret> (t != null) x = 1;
  }
}