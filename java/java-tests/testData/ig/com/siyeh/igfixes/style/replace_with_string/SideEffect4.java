class X {

  void n() {
    int p = 0;
    StringBuilder b<caret> = new StringBuilder();
    b.append(p);
    p++;
    System.out.println(b.toString());
  }
}