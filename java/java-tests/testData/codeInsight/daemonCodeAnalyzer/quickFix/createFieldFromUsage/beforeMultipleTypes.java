// "Create Field 'fld'" "true"
class A {
    public void foo() {
      Object x = <caret>fld;
      fld = "";
    }
}