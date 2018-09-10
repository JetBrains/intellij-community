// "Invert 'if' condition" "false"
class C {
  boolean foo() {
    {
      if (tr<caret>ue) continue;
      if (false) return false;
    }
    return true;
  }
}