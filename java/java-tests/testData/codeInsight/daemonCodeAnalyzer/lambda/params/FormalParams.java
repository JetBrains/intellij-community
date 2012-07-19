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
}