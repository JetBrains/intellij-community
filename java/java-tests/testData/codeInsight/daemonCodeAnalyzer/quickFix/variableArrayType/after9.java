// "Change 'myArr' type to 'char[][]'" "true"
class A extends B {
    void m() {
        myArr = new char[][]{{<caret>'a'}};
    }
}

class B {
  protected char[][] myArr;
}