class MyData {
  private Set<String> myInitialized;

  @Override
  public boolean equals(Object obj) {
    return obj instanceof MyData && myInitialized.equals(obj.myIniti<caret>)
  }
}