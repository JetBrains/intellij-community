interface I {
  void m(int i);
}

class Foo {
  I ii = (@<error descr="'@Override' not applicable to type use">Override</error> final int k)->{
    int j = k;
  };
  I ii1 = (final int k)->{
    <error descr="Incompatible types. Found: 'int', required: 'java.lang.String'">String s = k;</error>
  };

  void bazz() {
    bar<error descr="'bar(I)' in 'Foo' cannot be applied to '(<lambda expression>)'">((String s) -> {
      System.out.println(s);})</error>;
    bar((int i) -> {System.out.println(i);});
  } 
  void bar(I i){}
}