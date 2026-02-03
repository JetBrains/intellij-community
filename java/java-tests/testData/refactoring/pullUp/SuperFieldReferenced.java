class Super {
  public String delegate;
}

class Inner extends Super {
  public void b<caret>ar() {
    super.delegate.substring(0);
  }
}