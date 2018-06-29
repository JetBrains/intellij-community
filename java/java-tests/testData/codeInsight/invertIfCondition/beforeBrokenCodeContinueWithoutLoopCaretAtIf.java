// "Invert 'if' condition" "false"
class C {
  boolean foo() {
    {
      <caret>if (true) continue;
      if (false) return false;
    }
    return true;
  }
}