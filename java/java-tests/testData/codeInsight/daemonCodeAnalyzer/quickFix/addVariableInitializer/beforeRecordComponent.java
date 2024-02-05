// "Initialize variable 'x'" "false"
record R(int x) {
  R(int x) {
    System.out.println(this<caret>.x);
  }
}
