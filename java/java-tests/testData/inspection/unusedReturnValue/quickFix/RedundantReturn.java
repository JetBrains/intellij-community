class RedundantReturn {

  int y;

  int <caret>x(String s) {
    if (s != null) {
      y = Integer.parseInt(s);
      return 1;
    }
    return 0;
  }

  void usage() {
    x("hi!")
  }
}