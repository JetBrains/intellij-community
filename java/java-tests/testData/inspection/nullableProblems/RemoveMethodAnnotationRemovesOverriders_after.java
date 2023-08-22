interface Foo {
  long getTime();
}

class Bar implements Foo {
  @Override
  public long getTime() {
    return 0;
  }
}