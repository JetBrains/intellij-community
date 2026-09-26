class MyTest {
    
    MyTest bar() {
      return this;
    }
    
  {
    new MyTest()
      .bar()
      .bar()
      .bar()
      .bar();
  }
}