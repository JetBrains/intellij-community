// "Invert 'if' condition" "true"
class A {
    public boolean foo() {
      if ((<caret>))
        return false;
      else
        return true;
    }
}