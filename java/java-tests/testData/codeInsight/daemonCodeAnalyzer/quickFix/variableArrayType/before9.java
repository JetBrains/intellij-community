// "Change field 'myArr' type to 'char[][]'" "true-preview"
class A extends B {
    void m() {
        myArr = new String[][]{{<caret>'a'}};
    }
}

class B {
  protected String[][] myArr;
}