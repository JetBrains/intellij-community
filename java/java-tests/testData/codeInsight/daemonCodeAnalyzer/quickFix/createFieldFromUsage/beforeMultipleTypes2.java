// "Create field 'fld'" "true-preview"
class A {
    public void foo() {
      Object x = <caret>fld;
      String s = fld;
    }
}