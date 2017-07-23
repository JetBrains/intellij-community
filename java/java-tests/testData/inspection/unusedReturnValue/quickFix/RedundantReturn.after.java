class RedundantReturn {

  int y;

  void x(String s) {
    if (s != null) {
      y = Integer.parseInt(s);
    }
  }

  void usage() {
    x("hi!")
  }
}