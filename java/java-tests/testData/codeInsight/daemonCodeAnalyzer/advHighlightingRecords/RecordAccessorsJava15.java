record CheckOverride(int x) {
  @Override public int x() { return x; }
  <error descr="Method does not override method from its superclass">@Override</error> public int y() { return x; }
}