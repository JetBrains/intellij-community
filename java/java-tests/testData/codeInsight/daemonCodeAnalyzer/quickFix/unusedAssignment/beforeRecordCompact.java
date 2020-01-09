// "Remove redundant assignment" "false"
record R(int x) {
  R {
    <caret>x = x + 1;
  }
}