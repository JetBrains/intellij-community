class MyTest {
    
    MyTest foo() {
      return this;
    }
    
  {
    new MyTest()
      .foo()
      .foo()
      .foo()
      .<selection>f<caret>oo</selection>();
  }
}