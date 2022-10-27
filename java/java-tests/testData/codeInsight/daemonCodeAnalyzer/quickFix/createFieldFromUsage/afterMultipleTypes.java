// "Create field 'fld'" "true-preview"
class A {
    private String fld<caret>;

    public void foo() {
      Object x = fld;
      fld = "";
    }
}