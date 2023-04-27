class Test {
  int x = 0;

  void log(Object... objects) {
    for (Object object : objects) {
      System.out.println(object);
    }
  }

  void addOne() {
    log(x);
    x++;
  }

}