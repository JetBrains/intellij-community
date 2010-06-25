// "Create Field 'fld'" "true"
class A {
    private String fld<caret>;

    public void foo() {
      Object x = fld;
      String s = fld;
    }
}