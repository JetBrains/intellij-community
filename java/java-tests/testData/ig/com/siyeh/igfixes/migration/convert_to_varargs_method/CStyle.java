class CStyle {
  public void foo(final String a<caret>rg[]) {
  }

  {
    foo(new String[] {"one"});
  }
}