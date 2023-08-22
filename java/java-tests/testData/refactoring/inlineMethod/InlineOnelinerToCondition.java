class MyWorld {
  public boolean isH<caret>ello(Object o) {
    return o instanceof String && ((String)o).startsWith("hello");
  }

  public void process(Object[] o) {
    int i = 0;
    while (isHello(o[i])) {
      i++;
    }
    i = 0;
    while (!isHello(o[i])) {
      i++;
    }
  }
}
