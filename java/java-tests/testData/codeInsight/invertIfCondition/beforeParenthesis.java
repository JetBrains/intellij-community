// "Invert 'if' condition" "true"
class A {
    public boolean foo(boolean a, boolean b, boolean c, boolean d) {

      if (!(a |<caret>| b) || c || d)
        return false;
      return true;
    }
}