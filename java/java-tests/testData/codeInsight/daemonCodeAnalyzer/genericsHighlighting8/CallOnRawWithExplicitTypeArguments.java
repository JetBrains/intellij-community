class RawTest<A> {
  public <T> T foo() {
    return null;
  }
    
  void bar(RawTest x){
    <error descr="Incompatible types. Found: 'java.lang.Object', required: 'java.lang.String'">String a = x.<String>foo();</error>
  }
}