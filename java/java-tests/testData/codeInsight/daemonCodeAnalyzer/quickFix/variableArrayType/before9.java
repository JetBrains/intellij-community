// "Change 'myArr' type to 'char[][]'" "true"
class A extends B {
    void m() {
        myArr = new String[][]{{<caret>'a'}};
    }
}

class B {
  protected String[][] myArr;
}