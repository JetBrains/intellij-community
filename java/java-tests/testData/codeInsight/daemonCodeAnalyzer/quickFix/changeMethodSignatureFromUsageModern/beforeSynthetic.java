// "Add 'int' as 1st parameter to method 'x()'" "false"
record Point2(int x, int y) {
  public static void main(String[] args) {
    new Point2(0, 1).x(1<caret>);
  }
}