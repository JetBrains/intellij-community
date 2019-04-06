class A{
  {
      @Foo int <caret>i;
      i = 0;
  }
  
  @interface Foo {}
}
