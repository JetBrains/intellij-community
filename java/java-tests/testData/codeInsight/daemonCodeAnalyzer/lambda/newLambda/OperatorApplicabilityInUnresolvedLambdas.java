class Test {
  int field;
  
  void foo() {
    <error descr="Not a statement">i -> i + 1;</error>
    <error descr="Not a statement">i -> 1 + i;</error>
    <error descr="Not a statement">i -> 1 + 2 + i;</error>
    <error descr="Not a statement">i -> 1 + i + 2;</error>
    i -> <error descr="Operator '-' cannot be applied to 'int', 'java.lang.String'">1 - "xyz" - i</error>;
    
    foo<error descr="Expected no arguments but found 1">(i -> ++i)</error>;

    <error descr="Cannot resolve method 'bar' in 'Test'">bar</error>(i -> i += 2);
    <error descr="Cannot resolve method 'bar' in 'Test'">bar</error>(i -> field += i);
  }
}