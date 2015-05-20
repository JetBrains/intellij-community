// "Create field 'fld'" "true"
class A {
    public void foo() {
      Object x = <caret>fld;
      fld = "";
    }
}