class Parameter {
  public void test1(String arr<caret>[]) {
  }

  public void test2(@Required Integer @Required @Preliminary[] arr @Required @Preliminary[]) {
  }

  public void test3(@Required Integer arr    @Required @Preliminary [  ] @Preliminary[]) {
  }

  public void test4(Integer @Required   @Preliminary[] arr  []) {
  }

  public void test5(Integer [] arr @Required [] []) {
  }
}