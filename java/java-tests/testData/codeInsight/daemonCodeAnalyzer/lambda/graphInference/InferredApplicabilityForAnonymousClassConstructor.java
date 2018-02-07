abstract class Test {

  public <T> Test(final T t, int i) { }

  {
    Test action = new Test<error descr="'Test(T, int)' in 'Test' cannot be applied to '(java.lang.String, java.lang.String)'">("abc", "name")</error> {};
  }
}