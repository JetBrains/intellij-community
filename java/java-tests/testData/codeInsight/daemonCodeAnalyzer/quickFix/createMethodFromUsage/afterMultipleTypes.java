// "Create Method 'bar'" "true"
class A {
    public void foo() {
      Object x = bar();
      String s = bar();
    }

    private String bar() {
        <selection>return null;  //To change body of created methods use File | Settings | File Templates.<caret></selection>
    }
}