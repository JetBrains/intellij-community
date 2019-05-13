class A {
  @org.jetbrains.annotations.Contract("null,_->fail")  
  public int method<caret>(Object i, Object j) {
        return 0;
    }
}
