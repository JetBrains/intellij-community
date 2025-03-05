class RawTest<A> {
  public <T> T foo() {
    return null;
  }
    
  void bar(RawTest x){
    String a = x.<String><error descr="Incompatible types. Found: 'java.lang.Object', required: 'java.lang.String'">foo</error>();
  }
}