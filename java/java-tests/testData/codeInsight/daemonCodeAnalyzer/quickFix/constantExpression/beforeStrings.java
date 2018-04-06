// "Fix all 'Constant expression can be evaluated' problems in file" "true"
class Test {
  void test() {
    String[] squares = new String[10];
    squares[0] = 0 <caret>+ "*" + 0 + "=" + 0 * 0;
    squares[1] = 1 + "*" + 1 + "=" + 1 * 1;
    squares[2] = 2 + "*" + 2 + "=" + 2 * 2;
    squares[3] = 3 + "*" + 3 + "=" + 3 * 3;
    squares[4] = 4 + "*" + 4 + "=" + 4 * 4;
    squares[5] = 5 + "*" + 5 + "=" + 5 * 5;
    squares[6] = 6 + "*" + 6 + "=" + 6 * 6;
    squares[7] = 7 + "*" + 7 + "=" + 7 * 7;
    squares[8] = 8 + "*" + 8 + "=" + 8 * 8;
    squares[9] = 9 + "*" + 9 + "=" + 9 * 9;
  }
}