interface I {
  void setBar(int o);
}

interface Foo {
  void setBar(int bar);
}

class Bar implements Foo, I {
  int bar;

  @Override
  public void setBar(int bar) {
    this.bar = bar;
  }
}