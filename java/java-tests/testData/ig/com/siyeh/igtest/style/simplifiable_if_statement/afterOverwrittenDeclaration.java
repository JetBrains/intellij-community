// "Replace 'if else' with '?:'" "INFORMATION"
class OverwrittenDeclaration {

  void x(Object t) {
    int x = t != null ? 1 : 0;
  }
}