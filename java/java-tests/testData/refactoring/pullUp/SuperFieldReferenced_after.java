class Super {
  public String delegate;

    public void bar() {
      delegate.substring(0);
    }
}

class Inner extends Super {
}