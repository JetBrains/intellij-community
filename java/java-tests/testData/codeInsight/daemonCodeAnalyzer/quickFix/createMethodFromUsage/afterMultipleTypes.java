// "Create method 'bar'" "true-preview"
class A {
    public void foo() {
      Object x = bar();
      String s = bar();
    }

    private String bar() {
        <caret><selection>return null;</selection>
    }
}